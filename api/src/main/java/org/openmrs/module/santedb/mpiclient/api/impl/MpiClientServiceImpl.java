package org.openmrs.module.santedb.mpiclient.api.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dcm4che3.audit.AuditMessage;
import org.marc.everest.datatypes.II;
import org.marc.everest.formatters.interfaces.IXmlStructureFormatter;
import org.openmrs.Encounter;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PersonAddress;
import org.openmrs.PersonName;
import org.openmrs.Relationship;
import org.openmrs.Visit;
import org.openmrs.api.DuplicateIdentifierException;
import org.openmrs.api.context.Context;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.santedb.mpiclient.api.AtnaAuditService;
import org.openmrs.module.santedb.mpiclient.api.MpiClientService;
import org.openmrs.module.santedb.mpiclient.configuration.MpiClientConfiguration;
import org.openmrs.module.santedb.mpiclient.dao.MpiClientDao;
import org.openmrs.module.santedb.mpiclient.exception.MpiClientException;
import org.openmrs.module.santedb.mpiclient.model.MpiPatient;
import org.openmrs.module.santedb.mpiclient.util.AuditUtil;
import org.openmrs.module.santedb.mpiclient.util.MessageUtil;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v25.message.QBP_Q21;
import ca.uhn.hl7v2.util.Terser;

/**
 * Implementation of the health information exchange service
 * @author Justin
 *
 */
public class MpiClientServiceImpl extends BaseOpenmrsService
		implements MpiClientService {

	// Log
	private static Log log = LogFactory.getLog(MpiClientServiceImpl.class);
	// Message utility
	private MessageUtil m_messageUtil = MessageUtil.getInstance();
	// Get health information exchange information
	private MpiClientConfiguration m_configuration = MpiClientConfiguration.getInstance();

	// DAO
	private MpiClientDao dao;
	
	/**
	 * @param dao the dao to set
	 */
	public void setDao(MpiClientDao dao) {
		this.dao = dao;
	}

	/**
	 * Update patient ECID 
	 * @throws MpiClientException 
	 */
	public void synchronizePatientEnterpriseId(Patient patient) throws MpiClientException
	{
		// Resolve patient identifier
		PatientIdentifier pid = this.resolvePatientIdentifier(patient, this.m_configuration.getEnterprisePatientIdRoot());
		if(pid != null)
		{
			PatientIdentifier existingPid = patient.getPatientIdentifier(pid.getIdentifierType());
			if(existingPid != null && !existingPid.getIdentifier().equals(pid.getIdentifier()))
			{
					existingPid.setIdentifier(pid.getIdentifier());
					Context.getPatientService().savePatientIdentifier(existingPid);	
			}
			else if(existingPid == null)
			{
				pid.setPatient(patient);
				Context.getPatientService().savePatientIdentifier(pid);
			}
			else
				return;
		}
		else
			throw new MpiClientException("Patient has been removed from the HIE");
	}
	
	/**
	 * Search the PDQ supplier for the specified patient data
	 * @throws MpiClientException 
	 */
	public List<MpiPatient> searchPatient(String familyName, String givenName,
			Date dateOfBirth, boolean fuzzyDate, String gender,
			PatientIdentifier identifier,
			PatientIdentifier mothersIdentifier) throws MpiClientException {

		Map<String, String> queryParams = new HashMap<String, String>();
		if(familyName != null && !familyName.isEmpty())
			queryParams.put("@PID.5.1", familyName);
		if(givenName != null && !givenName.isEmpty())
			queryParams.put("@PID.5.2", givenName);
		if(dateOfBirth != null)
		{
			if(fuzzyDate)
				queryParams.put("@PID.7", new SimpleDateFormat("yyyy").format(dateOfBirth));
			else
				queryParams.put("@PID.7", new SimpleDateFormat("yyyyMMdd").format(dateOfBirth));
		}
		if(gender != null && !gender.isEmpty())
			queryParams.put("@PID.8", gender);
		if(identifier != null)
		{
			queryParams.put("@PID.3.1", identifier.getIdentifier());
			
			if(identifier.getIdentifierType() != null)
			{
				if(II.isRootOid(new II(identifier.getIdentifierType().getName())))
				{
					queryParams.put("@PID.3.4.2", identifier.getIdentifierType().getName());
					queryParams.put("@PID.3.4.3", "ISO");
				}
				else
					queryParams.put("@PID.3.4", identifier.getIdentifierType().getName());
			}
		}
		if(mothersIdentifier != null)
		{
			
			queryParams.put("@PID.21.1", mothersIdentifier.getIdentifier());
			
			if(mothersIdentifier.getIdentifierType() != null)
			{
				if(II.isRootOid(new II(mothersIdentifier.getIdentifierType().getName())))
				{
					queryParams.put("@PID.21.4.2", mothersIdentifier.getIdentifierType().getName());
					queryParams.put("@PID.21.4.3", "ISO");
				}
				else
					queryParams.put("@PID.21.4", mothersIdentifier.getIdentifierType().getName());
			}
		}
			
		AtnaAuditService auditSvc= Context.getService(AtnaAuditService.class);
		AuditMessage auditMessage = null;
		Message pdqRequest = null;
		
		// Send the message and construct the result set
		try
		{
			pdqRequest = this.m_messageUtil.createPdqMessage(queryParams);
			Message	response = this.m_messageUtil.sendMessage(pdqRequest, this.m_configuration.getPdqEndpoint(), this.m_configuration.getPdqPort());
			
			Terser terser = new Terser(response);
			if(!terser.get("/MSA-1").endsWith("A"))
				throw new MpiClientException("Error querying data");
			
			
			List<MpiPatient> retVal = this.m_messageUtil.interpretPIDSegments(response);
			auditMessage = AuditUtil.getInstance().createPatientSearch(retVal, this.m_configuration.getPdqEndpoint(), (QBP_Q21)pdqRequest);
			return retVal;
		}
		catch(Exception e)
		{
			log.error("Error in PDQ Search", e);
			if(pdqRequest != null)
				try {
					auditMessage = AuditUtil.getInstance().createPatientSearch(null, this.m_configuration.getPdqEndpoint(), (QBP_Q21)pdqRequest);
				} catch (UnknownHostException e1) {
					this.log.error("Error creating error audit:", e1);
				}

			throw new MpiClientException(e);
		}
		finally
		{
			if(auditMessage != null)
				try {
					auditSvc.getLogger().write(Calendar.getInstance(), auditMessage);
				} catch (Exception e) {
					log.error(e);
				}
		}
	}

	/**
	 * Search the PDQ supplier for the specified patient data with identifier
	 * @throws MpiClientException 
	 */
	public MpiPatient getPatient(String identifier,
			String assigningAuthority) throws MpiClientException {

		Map<String, String> queryParameters = new HashMap<String, String>();
		queryParameters.put("@PID.3.1", identifier);
		queryParameters.put("@PID.3.4.2", assigningAuthority);
		queryParameters.put("@PID.3.4.3", "ISO");

		// Auditing stuff
		AtnaAuditService auditSvc= Context.getService(AtnaAuditService.class);
		AuditMessage auditMessage = null;
		Message request = null;
		
		try
		{
			request = this.m_messageUtil.createPdqMessage(queryParameters);
			Message response = this.m_messageUtil.sendMessage(request, this.m_configuration.getPdqEndpoint(), this.m_configuration.getPdqPort());
			
			List<MpiPatient> pats = this.m_messageUtil.interpretPIDSegments(response);
			auditMessage = AuditUtil.getInstance().createPatientSearch(pats, this.m_configuration.getPdqEndpoint(), (QBP_Q21)request);

			if(pats.size() > 1)
				throw new DuplicateIdentifierException("More than one patient exists");
			else if(pats.size() == 0)
				return null;
			else
				return pats.get(0);
		}
		catch(Exception e)
		{
			log.error("Error in PDQ Search", e);

			if(request != null)
				try {
					auditMessage = AuditUtil.getInstance().createPatientSearch(null, this.m_configuration.getPdqEndpoint(), (QBP_Q21)request);
				} catch (UnknownHostException e1) {
					// TODO Auto-generated catch block
					this.log.error("Error creating error audit:", e1);
				}

			throw new MpiClientException(e);
		}
		finally
		{
			if(auditMessage != null)
				try {
					auditSvc.getLogger().write(Calendar.getInstance(), auditMessage);
				} catch (Exception e) {
					log.error(e);
				}
		}
	}

	/**
	 * Import the patient from the PDQ supplier
	 * @throws MpiClientException 
	 */
	public Patient importPatient(MpiPatient patient) throws MpiClientException 
	{
		Patient patientRecord = this.matchWithExistingPatient(patient);
		
		// Existing? Then update this from that
		if(patientRecord != null)
		{
			
			// Add new identifiers
			for(PatientIdentifier id : patient.getIdentifiers())
			{
				boolean hasId = false;
				for(PatientIdentifier eid : patientRecord.getIdentifiers())
					hasId |= eid.getIdentifier().equals(id.getIdentifier()) && eid.getIdentifierType().getId().equals(id.getIdentifierType().getId());
				if(!hasId)
					patientRecord.getIdentifiers().add(id);
			}
			
			// update names
			patientRecord.getNames().clear();
			for(PersonName name : patient.getNames())
				patientRecord.addName(name);
			// update addr
			patientRecord.getAddresses().clear();
			for(PersonAddress addr : patient.getAddresses())
				patientRecord.addAddress(addr);
			
			// Update deceased
			patientRecord.setDead(patient.getDead());
			patientRecord.setDeathDate(patient.getDeathDate());
			patientRecord.setBirthdate(patient.getBirthdate());
			patientRecord.setBirthdateEstimated(patient.getBirthdateEstimated());
			patientRecord.setGender(patient.getGender());
			
		}
		else
		{
			boolean isPreferred = false;
			for(PatientIdentifier id : patient.getIdentifiers())
				if(id.getIdentifierType().getName().equals(this.m_configuration.getPreferredPatientIdRoot()) ||
						id.getIdentifierType().getUuid().equals(this.m_configuration.getPreferredPatientIdRoot()))
				{
					id.setPreferred(true);
					isPreferred = true;
				}
			
			if(!isPreferred)
				patient.getIdentifiers().iterator().next().setPreferred(true);
			patientRecord = patient;
		}
		
		Patient importedPatient = Context.getPatientService().savePatient(patientRecord);
		
		// Now setup the relationships
		if(patient instanceof MpiPatient &&
				this.m_configuration.getUseOpenMRSRelationships()) {
			MpiPatient mpiPatient = (MpiPatient)patient;

			// Insert relationships
			for(Relationship rel : mpiPatient.getRelationships()) {
				Context.getPersonService().saveRelationship(new Relationship(importedPatient, rel.getPersonB(), rel.getRelationshipType()));
			}
		}
		
		// Now notify the MPI
		this.exportPatient(patient);
		return importedPatient;
	}
	
	/**
	 * Export a patient to the HIE
	 * @throws MpiClientException 
	 */
	public void exportPatient(Patient patient) throws MpiClientException {
		// TODO Auto-generated method stub
		
		Message admitMessage = null;
		AtnaAuditService auditSvc = Context.getService(AtnaAuditService.class);
		AuditMessage auditMessage = null;

		try
		{
			admitMessage = this.m_messageUtil.createAdmit(patient);
			Message response = this.m_messageUtil.sendMessage(admitMessage, this.m_configuration.getPixEndpoint(), this.m_configuration.getPixPort());
			
			Terser terser = new Terser(response);
			if(!terser.get("/MSA-1").endsWith("A"))
				throw new MpiClientException("Error querying data");
			auditMessage = AuditUtil.getInstance().createPatientAdmit(patient, this.m_configuration.getPixEndpoint(), admitMessage, true);

		}
		catch(Exception e)
		{
			log.error(e);
			if(auditMessage != null)
				auditMessage = AuditUtil.getInstance().createPatientAdmit(patient, this.m_configuration.getPixEndpoint(), admitMessage, false);

			throw new MpiClientException(e);
		}
		finally
		{
			if(auditMessage != null)
				try
				{
					auditSvc.getLogger().write(Calendar.getInstance(), auditMessage);
				}
				catch(Exception e)
				{
					log.error(e);
				}
		}	

	}

	/**
	 * Resolve patient identifier of the patient
	 * @throws MpiClientException 
	 */
	public PatientIdentifier resolvePatientIdentifier(Patient patient,
			String toAssigningAuthority) throws MpiClientException {
		
		AtnaAuditService auditSvc = Context.getService(AtnaAuditService.class);
		AuditMessage auditMessage = null;

		Message request = null;
		try
		{
			request = this.m_messageUtil.createPixMessage(patient, toAssigningAuthority);
			Message response = this.m_messageUtil.sendMessage(request, this.m_configuration.getPixEndpoint(), this.m_configuration.getPixPort());
			
			// Interpret the result
			List<MpiPatient> candidate = this.m_messageUtil.interpretPIDSegments(response);
			auditMessage = AuditUtil.getInstance().createPatientResolve(candidate, this.m_configuration.getPixEndpoint(), request);
			if(candidate.size() == 0)
				return null;
			else
				return candidate.get(0).getIdentifiers().iterator().next();
		}
		catch(Exception e)
		{
			log.error(e);
			if(request != null)
				auditMessage = AuditUtil.getInstance().createPatientResolve(null, this.m_configuration.getPixEndpoint(), request);

			throw new MpiClientException(e);
		}
		finally
		{
			if(auditMessage != null)
				try
				{
					auditSvc.getLogger().write(Calendar.getInstance(), auditMessage);
				}
				catch(Exception e)
				{
					log.error(e);
				}
		}
	}

	/**
	 * Match an external patient with internal patient
	 * @see org.openmrs.module.santedb.mpiclient.api.MpiClientService#matchWithExistingPatient(org.openmrs.Patient)
	 */
	public Patient matchWithExistingPatient(Patient remotePatient) {
		Patient candidate = null;
		// Does this patient have an identifier from our assigning authority?
		for(PatientIdentifier pid : remotePatient.getIdentifiers())
			if(pid.getIdentifierType().getName().equals(this.m_configuration.getLocalPatientIdRoot()))
				try
				{
					candidate = Context.getPatientService().getPatient(Integer.parseInt(pid.getIdentifier()));
				}
				catch(Exception e)
				{
					
				}
		
		// This patient may be an existing patient, so we just don't want to add it!
		if(candidate == null)
			for(PatientIdentifier pid : remotePatient.getIdentifiers())
			{
				candidate = this.dao.getPatientByIdentifier(pid.getIdentifier(), pid.getIdentifierType());
				if(candidate != null)
					break;
			}
		
		return candidate;
    }

	/**
	 * Update the patient record
	 * @see org.openmrs.module.santedb.mpiclient.api.MpiClientService#updatePatient(org.openmrs.Patient)
	 */
	public void updatePatient(Patient patient) throws MpiClientException {
		
		// TODO Auto-generated method stub
		AtnaAuditService auditSvc = Context.getService(AtnaAuditService.class);
		AuditMessage auditMessage = null;

		Message admitMessage = null;
		try
		{
			admitMessage = this.m_messageUtil.createUpdate(patient);
			Message	response = this.m_messageUtil.sendMessage(admitMessage, this.m_configuration.getPixEndpoint(), this.m_configuration.getPixPort());
			
			Terser terser = new Terser(response);
			if(!terser.get("/MSA-1").endsWith("A"))
				throw new MpiClientException("Error querying data");
			auditMessage = AuditUtil.getInstance().createPatientAdmit(patient, this.m_configuration.getPixEndpoint(), admitMessage, true);


		}
		catch(Exception e)
		{
			log.error(e);
			if(auditMessage != null)
				auditMessage = AuditUtil.getInstance().createPatientAdmit(patient, this.m_configuration.getPixEndpoint(), admitMessage, false);

			throw new MpiClientException(e);
		}
		finally
		{
			if(auditMessage != null)
				try
				{
					auditSvc.getLogger().write(Calendar.getInstance(), auditMessage);
				}
				catch(Exception e)
				{
					log.error(e);
				}
		}	
		
    }

}
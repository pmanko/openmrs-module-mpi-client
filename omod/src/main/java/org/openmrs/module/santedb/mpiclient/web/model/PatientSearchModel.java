package org.openmrs.module.santedb.mpiclient.web.model;

/**
 * Patient Search model
 * @author Justin
 *
 */
public class PatientSearchModel {

	private String identifier;
	private String momsId;
	private String relativeName;
	private String birthPlace;
	
	public String getRelativeName() { return this.relativeName; }
	public void setRelativeName(String value) { this.relativeName = value; }
	public String getBirthPlace() { return this.birthPlace; }
	public void setBirthPlace(String value) { this.birthPlace = value; }
	/**
	 * @return the identifier
	 */
	public String getIdentifier() {
		return identifier;
	}
	/**
	 * @param identifier the identifier to set
	 */
	public void setIdentifier(String identifier) {
		this.identifier = identifier;
	}
	/**
	 * @return the momsId
	 */
	public String getMomsId() {
		return momsId;
	}
	/**
	 * @param momsId the momsId to set
	 */
	public void setMomsId(String momsId) {
		this.momsId = momsId;
	}
	
	// Family name
		private String address;
	// Family name
	private String familyName;
	// Given name
	private String givenName;
	// Gender
	private String gender;
	// Date of birth
	private String dateOfBirth;
	/**
	 * @return the familyName
	 */
	public String getFamilyName() {
		return familyName;
	}
	/**
	 * @param familyName the familyName to set
	 */
	public void setFamilyName(String familyName) {
		this.familyName = familyName;
	}
	/**
	 * @return the givenName
	 */
	public String getGivenName() {
		return givenName;
	}
	/**
	 * @param givenName the givenName to set
	 */
	public void setGivenName(String givenName) {
		this.givenName = givenName;
	}
	/**
	 * @return the gender
	 */
	public String getGender() {
		return gender;
	}
	/**
	 * @param gender the gender to set
	 */
	public void setGender(String gender) {
		this.gender = gender;
	}
	/**
	 * @return the dateOfBirth
	 */
	public String getDateOfBirth() {
		return dateOfBirth;
	}
	/**
	 * @param dateOfBirth the dateOfBirth to set
	 */
	public void setDateOfBirth(String dateOfBirth) {
		this.dateOfBirth = dateOfBirth;
	}
	
	/*
	 * @return the familyName
	 */
	public String getAddress() {
		return address;
	}
	/**
	 * @param familyName the familyName to set
	 */
	public void setAddress(String address) {
		this.address = address;
	}
}

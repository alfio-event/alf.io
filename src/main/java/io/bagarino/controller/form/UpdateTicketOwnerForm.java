package io.bagarino.controller.form;

import lombok.Data;

@Data
public class UpdateTicketOwnerForm {
	private String email;
	private String fullName;
	private String jobTitle;
	private String company;
	private String phoneNumber;
	private String address;
	private String country;
	private String tShirtSize;
}
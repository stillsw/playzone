package com.courseracapstone.serverside.auth;

import java.util.ArrayList;
import java.util.Collection;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

public class MyInMemoryUserDetailsManager extends InMemoryUserDetailsManager {

	private static MyInMemoryUserDetailsManager instance;
	
	private MyInMemoryUserDetailsManager(Collection<UserDetails> users) {
		super(users);
	}
	
	public static MyInMemoryUserDetailsManager getInstance() {
		return instance;
	}	

	public static MyInMemoryUserDetailsManager newInstance(ArrayList<UserDetails> users) {
		instance = new MyInMemoryUserDetailsManager(users);
		return instance;
	}

	public void appCreateUser(UserDetails userDetails) {
		super.createUser(userDetails);
	}
}

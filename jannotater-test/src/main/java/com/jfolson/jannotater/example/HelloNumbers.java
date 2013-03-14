package com.jfolson.jannotater.example;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.Serializable;
import java.util.Arrays;
import com.jfolson.jannotater.RJava;

public class HelloNumbers extends HelloWorld<Double> {
	/**
	 * HelloWorld with a Double-value message
	 * 
	 * @param value double value to display
	 * 
	 * @examples
	 * library(HelloWorld)
	 * getMessage(HelloNumbers(1.5)) ## 1.5
	 * \dontshow{
	 * library(testthat)
	 * expect_true(getMessage(HelloNumbers(1.5)) == 1.5)
	 * }
	 */
	@RJava
	public HelloNumbers(double value)
	{
		super(new Double(value));
	}

}

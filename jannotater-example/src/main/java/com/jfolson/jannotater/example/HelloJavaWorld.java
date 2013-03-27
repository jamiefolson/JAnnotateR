package com.jfolson.jannotater.example;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.Serializable;
import java.util.Arrays;
import org.rosuda.JRI.Rengine;
import org.rosuda.REngine.JRI.JRIEngine;
import org.rosuda.REngine.REXP;
import org.rosuda.REngine.REXPMismatchException;
import org.rosuda.REngine.REXPString;
import org.rosuda.REngine.REngine;
import org.rosuda.REngine.REngineException;
import org.rosuda.REngine.RList;
import com.jfolson.jannotater.RJava;

public class HelloJavaWorld extends HelloWorld<String>
implements Serializable, ActionListener, ItemListener
{
	public HelloJavaWorld()
	{
		this("Hello Java World!");
	}

	@RJava
	public static String staticHello() {
		return "Static Hello Java World!";
	}

	/**
	 * A String-based HelloWorld class
	 * @param message String message to say
	 * @examples
	 * library(HelloWorld)
	 * getMessage(HelloJavaWorld("Hello!")) ## "Hello!"
	 * \dontshow{
	 * library(testthat)
	 * expect_true(getMessage(HelloJavaWorld("Hello!")) == "Hello!")
	 * }
	 */
	@RJava
	public HelloJavaWorld(String message)
	{
		super(message);
	}

	@RJava
	public HelloJavaWorld getThis()
	{
		return this;
	}

	@RJava
	public static HelloJavaWorld getNew(String message)
	{
		return new HelloJavaWorld(message);
	}

	@RJava
	public String sayHello()
	{
		String result = new String("Hello Java World!");
		return result;
	}

	public String sayHello(String hello)
	{
		return hello;
	}

	@RJava
	public String speakMessage()
	{
		return (String)this.object;
	}

	@RJava
	public void sayVoid()
	{
	}

	/**
	 * Arrays don't need to be converted.
	 * 
	 * @return an array
	 * 
	 * @examples
	 * \dontshow{
	 * library(testthat)
	 * v = sayArray(HelloJavaWorld("yo"))
	 * expect_true(all(v == 1:3))
	 * expect_true(length(v) == 3)
	 * }
	 */
	@RJava
	public int[] sayArray()
	{
		return new int[] { 1, 2, 3 };
	}

	/**
	 * Test creating and converting a matrix.
	 * 
	 * There's absolutely no reason for this to be a method and not a static function.
	 * @return a matrix
	 * 
	 * @examples
	 * \dontshow{
	 * library(testthat)
	 * m = sayMatrix(HelloJavaWorld("yo"))
	 * expect_true(all(t(m) == 1:6))
	 * expect_true(all(dim(m) == c(2,3)))
	 * }
	 */
	@RJava(rReturn="{t(sapply(jreturnobj,.jevalArray))}")
	public int[][] sayMatrix()
	{
		return new int[][] { { 1, 2, 3 }, { 4, 5, 6 } };
	}

	@RJava
	public int isInteger(REXP exp)
	{
		if (exp.isInteger()) return 1;
		return 0;
	}

	@RJava
	public int isList(REXP exp)
	{
		if (exp.isList()) return 1;
		return 0;
	}

	/**
	 * Convert an REXP numeric value to an int and return it
	 * @param val Any numeric R value
	 * @return val converted to an int using \\code{REXP.asInteger()}
	 * @import rJava
	 * @examples
	 * library(HelloWorld)
	 * HelloJavaWorld.asInt(2.5) ## 2
	 * \dontshow{
	 * library(testthat)
	 * expect_true(HelloJavaWorld.asInt(2.5) == 2)
	 * }
	 */
	@RJava(rBefore=".jengine(TRUE)")
	public static int asInt(REXP val)
	{
		int intVal = 0;
		try {
			intVal = val.asInteger();
		}
		catch (REXPMismatchException e) {
			e.printStackTrace();
		}
		return intVal;
	}

	@RJava
	public static double asDouble(REXP exp)
	{
		double intVal = 0.0D;
		try {
			intVal = exp.asDouble();
		}
		catch (REXPMismatchException e) {
			e.printStackTrace();
		}
		return intVal;
	}

	/**
	 * Create a character object in java and return it into R.
	 * 
	 * @return a string created in java
	 * @throws REngineException
	 * @throws REXPMismatchException
	 * 
	 * @import rJava
	 * @examples
	 * library(HelloWorld)
	 * HelloJavaWorld.testREngineCreate() ## "created in java"
	 * \dontshow{
	 * library(testthat)
	 * expect_true(HelloJavaWorld.testREngineCreate() == "created in java")
	 * }
	 */
	@RJava(rReturn=".robj",rBefore=".jengine(TRUE)")
	public static REXP testREngineCreate()
			throws REngineException, REXPMismatchException
			{
		REXP obj = new REXPString("created in java");
		REngine engine = new JRIEngine(Rengine.getMainEngine());
		engine.assign(".robj", obj);
		return obj;
			}

	/**
	 * Create a data.frame in java and return it into R
	 * @return a data.frame created in java
	 * @throws REngineException
	 * @throws REXPMismatchException
	 * 
	 * @import rJava
	 * @examples
	 * library(HelloWorld)
	 * HelloJavaWorld.createDataFrame()
	 * \dontshow{
	 * library(testthat)
	 * expect_true(all(HelloJavaWorld.createDataFrame() == data.frame("a","b","c")))
	 * }
	 */
	@RJava(rReturn=".robj",rBefore=".jengine(TRUE)")
	public static REXP createDataFrame() throws REngineException, REXPMismatchException {
		RList lst = new RList(Arrays.asList(new REXPString[] { new REXPString("a"), new REXPString("b"), new REXPString("c") }));
		REXP obj = REXP.createDataFrame(lst);
		REngine engine = new JRIEngine(Rengine.getMainEngine());
		engine.assign(".robj", obj);
		return obj;
	}

	public void itemStateChanged(ItemEvent arg0)
	{
	}

	public void actionPerformed(ActionEvent arg0)
	{
	}
}

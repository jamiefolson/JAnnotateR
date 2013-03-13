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

	@RJava
	public int[] sayArray()
	{
		return new int[] { 1, 2, 3 };
	}

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

	@RJava
	public static int asInt(REXP exp)
	{
		int intVal = 0;
		try {
			intVal = exp.asInteger();
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

	@RJava(rArgs="")
	public static void testMatrix(REXP matrix, REXP from)
	{
	}

	@RJava(rReturn=".robj")
	public static REXP testREngineCreate()
			throws REngineException, REXPMismatchException
			{
		REXP obj = new REXPString("created in java");
		REngine engine = new JRIEngine(Rengine.getMainEngine());
		engine.assign(".robj", obj);
		return obj;
			}

	@RJava(rReturn=".robj")
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

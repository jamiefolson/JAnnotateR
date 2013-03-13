package com.jfolson.jannotater.example;
import com.jfolson.jannotater.RJava;


public class HelloWorld<T>
{
	T object;

	/**
	 * Create an object to say hello to the world!
	 * @param obj message to display
	 */
	@RJava(rName="Hello")
	public HelloWorld(T obj)
	{
		this.object = obj;
	}

	/**
	 * Retrieve this object's message
	 * @return the message
	 */
	@RJava
	public T getMessage() {
		return this.object;
	}
}

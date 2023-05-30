package com.projects.aws;


import javax.imageio.ImageIO;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Base64;
import java.util.UUID;

import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;

import org.json.JSONObject;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class Servlet extends HttpServlet {

  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,IOException{
	  response.setContentType("text/plain");
	  response.getWriter().write("Your server is running");
  }
  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
	  
	  byte[] imageBytes;
	  byte[] flippedImageBytes;
	  JSONObject responseJson;
	  
      try {
    	  
    	  BufferedReader reader = request.getReader();
    	  
    	  JsonObject jsonRequest = new Gson().fromJson(reader, JsonObject.class);
    	    reader.close();
    	    
    	  responseJson = new JSONObject();
    	  

    	  	
    	  	imageBytes = Base64.getDecoder().decode(jsonRequest.get("image").getAsString());
    	  	ByteArrayInputStream inputStream = new ByteArrayInputStream(imageBytes);
	        
    	    BufferedImage originalImage = ImageIO.read(inputStream);
    	    
	        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
	        ImageIO.write(originalImage, "png", outputStream);
	        
	        
	        byte[] inputImageBytes = outputStream.toByteArray();
	        
	        
	        byte[] processedImageBytes = Client.getProcessedImageBytes(inputImageBytes);

	        inputStream = new ByteArrayInputStream(processedImageBytes);

    	    
    	    BufferedImage flippedImage = ImageIO.read(inputStream);;

    	    outputStream = new ByteArrayOutputStream();
    	    
    	    ImageIO.write(flippedImage, "png", outputStream);
    	    
    	    flippedImageBytes = outputStream.toByteArray();

    	    
    	    responseJson.put("type", "success");
    	    responseJson.put("data", Base64.getEncoder().encodeToString(flippedImageBytes));

    	    response.setContentType("application/json");

    	    PrintWriter writer = response.getWriter();
    	    writer.print(responseJson.toString());
    	    writer.close();
    	    
    	} catch (IOException e) {

    	    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    	    response.setContentType("application/json");

    	    JSONObject errorJson = new JSONObject();
    	    errorJson.put("type", "error");
    	    errorJson.put("message", "An error occurred from server side. Try uploading the image again.");

    	    PrintWriter writer = response.getWriter();
    	    writer.print(errorJson.toString());
    	    writer.close();
    	}
  }


}
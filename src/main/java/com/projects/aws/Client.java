package com.projects.aws;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.nio.file.Files;

public class Client {

	private static final String BUCKET_NAME = "assignment4-jaffery";

	private static final String INBOX_QUEUE_NAME = "inboxQueue";

	private static final String OUTBOX_QUEUE_NAME = "outboxQueue";

	private static String programDirectory = System.getProperty("user.dir");
	
	private static String DEFAULT_INPUT_PHOTO_LOCATION = programDirectory + "/src/main/java/com/projects/InputImages/photo.png";

	private static String OUTPUT_PHOTO_LOCATION = programDirectory + "/src/main/java/com/projects/OutputImages/processedImage.png";
	

	private static final Regions CURRENT_REGION = Regions.US_EAST_1;

	public static void main(String[] args) {
		
		String imagePath;
		File imageFile;
		byte[] inputImageBytes= null;
		
		
		
        if (args.length == 1) {
        	
        	imagePath = args[0];
       
        	
            if (!new File(imagePath).exists()) {
                System.out.println("The specified image path does not exist. Will be using default image path at " + DEFAULT_INPUT_PHOTO_LOCATION);
                imagePath = DEFAULT_INPUT_PHOTO_LOCATION;
            }

        }
        else {
        	System.out.println("No image file path provided. Will be using default image path at "+DEFAULT_INPUT_PHOTO_LOCATION);
        	imagePath = DEFAULT_INPUT_PHOTO_LOCATION;
        }
        
        
        imageFile = new File(imagePath);

        try {
        	
			inputImageBytes = Files.readAllBytes(imageFile.toPath());
			
		
		
		byte[] processedImageBytes = getProcessedImageBytes(inputImageBytes);
		
		Files.write(Paths.get(OUTPUT_PHOTO_LOCATION), processedImageBytes);
		
		System.out.println("Processed image is downloaded and stored at "+ OUTPUT_PHOTO_LOCATION);
		
		
        } catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
	}
	
	public static byte[] getProcessedImageBytes (byte[] imageBytes) {
		

		AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
				.withCredentials(DefaultAWSCredentialsProviderChain.getInstance()).withRegion(CURRENT_REGION)
				.build();

		AmazonSQS sqsClient = AmazonSQSClientBuilder.standard().withRegion(CURRENT_REGION)
				.build();


		String keyName = UUID.randomUUID().toString();
		
		String inboxQueueURL = sqsClient.getQueueUrl(INBOX_QUEUE_NAME).getQueueUrl();
		String outboxQueueURL = sqsClient.getQueueUrl(OUTBOX_QUEUE_NAME).getQueueUrl();
		

		try {
			
			InputStream inputStream = new ByteArrayInputStream(imageBytes);
			
			ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(imageBytes.length);
			
			s3Client.putObject(BUCKET_NAME, keyName, inputStream, metadata);

			System.out.println("Uploaded " + keyName + "to S3 Bucket");

		} catch (AmazonServiceException e) {
			System.err.println(e.getErrorMessage());
			System.exit(1);
		}
		
		
		System.out.println("Prepairing to send message to the Inbox Queue");

		SendMessageRequest sendMessageRequest = new SendMessageRequest()
				.withQueueUrl(inboxQueueURL)
				.withMessageBody(keyName)
				.withDelaySeconds(5);

		sqsClient.sendMessage(sendMessageRequest);

		System.out.println("Message sent to inbox queue");


		while (true) {

			System.out.println("Reading message from the outbox queue");

			List<Message> messages = sqsClient.receiveMessage(outboxQueueURL).getMessages();

			System.out.println("Found " + messages.size() + " messages in the outbox queue.");

			if (messages.size() > 0) {

				for (Message message : messages) {

					String responseKey = message.getBody();

					if (responseKey.equals(keyName)) {

						System.out.println("Found match key in outbox queue, start downloading");

						try {

							int bytesRead;

							S3Object s3Object = s3Client.getObject(BUCKET_NAME, responseKey+"180");

							S3ObjectInputStream inputStream = s3Object.getObjectContent();

							byte[] buffer = new byte[4096];
	

							ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

							while ((bytesRead = inputStream.read(buffer)) != -1) {
								outputStream.write(buffer, 0, bytesRead);
							}
							
							inputStream.close();
							

							sqsClient.deleteMessage(outboxQueueURL, message.getReceiptHandle());
							
							System.out.println("Deleting the message from outbox queue");
							
							return outputStream.toByteArray();
						}

						catch (IOException e) {
							System.err.println("Error processing image: " + e.getMessage());
						}

					}
				}

			}		
			
			System.out.println("Couldn't find the message in outbox queue yet, going to sleep for 2 seconds");
			
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return null;
			}
		}
		
	}
	
	
}
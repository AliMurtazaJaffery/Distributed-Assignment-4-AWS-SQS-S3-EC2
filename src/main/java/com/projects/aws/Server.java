package com.projects.aws;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;

import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageRequest;

public class Server {
	private static final String BUCKET_NAME = "assignment4-jaffery";

	private static final String INBOX_QUEUE_NAME = "inboxQueue";

	private static final String OUTBOX_QUEUE_NAME = "outboxQueue";

//	You can change it to any other format 
	
	private static final String FILE_FORMAT = ".png";

	private static final Regions CURRENT_REGION = Regions.US_EAST_1;
	
    public static void deleteFileLocally(String filePath) {
        File fileToDelete = new File(filePath);
        if (fileToDelete.exists()) {
            boolean deleted = fileToDelete.delete();
            if (deleted) {
                System.out.println("File deleted successfully at "+ filePath);
            } else {
                System.out.println("Error deleting file at "+filePath);
            }
        } else {
            System.out.println("File does not exist.");
        }
    }

	public static void main(String[] args) {
		
		AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
				.withCredentials(DefaultAWSCredentialsProviderChain.getInstance()).withRegion(CURRENT_REGION)
				.build();

		AmazonSQS sqsClient = AmazonSQSClientBuilder.standard().withRegion(CURRENT_REGION)
				.build();

		String inboxQueueURL = sqsClient.getQueueUrl(INBOX_QUEUE_NAME).getQueueUrl();
		String outboxQueueURL = sqsClient.getQueueUrl(OUTBOX_QUEUE_NAME).getQueueUrl();
		
		String programDirectory = System.getProperty("user.dir");
		
		while (true) {

			String key;

			List<Message> messages = sqsClient
					.receiveMessage(new ReceiveMessageRequest(inboxQueueURL).withMaxNumberOfMessages(1)).getMessages();
			
			System.out.println("Found " + messages.size() + " messages in the inbox queue.");
			
			if (messages.size() > 0) {

				Message message = messages.get(0);

				key = message.getBody();
				
				
				String filename = programDirectory + "/"+ key + FILE_FORMAT;
				

				try {

					int bytesRead;

					S3Object s3Object = s3Client.getObject(BUCKET_NAME, key);

					S3ObjectInputStream inputStream = s3Object.getObjectContent();

					byte[] buffer = new byte[4096];

					File outputFile = new File(filename);

					outputFile.createNewFile();

					FileOutputStream outputStream = new FileOutputStream(outputFile);

					while ((bytesRead = inputStream.read(buffer)) != -1) {
						outputStream.write(buffer, 0, bytesRead);
					}
					outputStream.close();
					inputStream.close();
					
					
					System.out.println("Image downloaded from S3");
				} catch (IOException e) {
					System.err.println(e.getMessage());
					System.exit(1);
				}


				System.out.println("Processing the image");
				
				String outputFileName = programDirectory + "/" + key + "180"+ FILE_FORMAT;
				
				String Command = "convert " + filename + " -rotate 180 " + outputFileName;
				System.out.println(Command);
				try {
					Runtime rt = Runtime.getRuntime();

					Process process = rt.exec(Command);
					System.out.println("Image is processed");
					
					

					Thread.sleep(2000);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					System.exit(1);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					System.exit(1);
				}

				s3Client.putObject(BUCKET_NAME, key+ "180", new File(outputFileName));

				System.out.println("Image uploaded to to S3");

				SendMessageRequest send_msg_request = new SendMessageRequest()
						.withQueueUrl(outboxQueueURL)
						.withMessageBody(key)
						.withDelaySeconds(5);
				sqsClient.sendMessage(send_msg_request);

				System.out.println("Send message to outbox queue");

				sqsClient.deleteMessage(INBOX_QUEUE_NAME, message.getReceiptHandle());
				
				System.out.println("Message deleted from inbox queue after successfully processing the image.");
				
				deleteFileLocally(filename);
				deleteFileLocally(outputFileName);
				

			} else {
				System.out.println("Couldn't find any message in the message queue. Sleeping for 10s.");
				try {
					Thread.sleep(10000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}
}
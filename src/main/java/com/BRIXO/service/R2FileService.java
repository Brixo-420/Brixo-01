package com.BRIXO.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;
import java.util.UUID;

@Service
public class R2FileService {

	private final S3Client s3Client;
	private final String bucketName;
	private final String r2Endpoint;

	public R2FileService(
			@Value("${r2.access-key-id:}") String accessKeyId,
			@Value("${r2.secret-access-key:}") String secretAccessKey,
			@Value("${r2.endpoint:}") String endpoint,
			@Value("${r2.bucket-name:brixo-uploads}") String bucketName) {

		this.bucketName = bucketName;
		this.r2Endpoint = endpoint;

		if (accessKeyId == null || accessKeyId.isBlank() ||
			secretAccessKey == null || secretAccessKey.isBlank() ||
			endpoint == null || endpoint.isBlank()) {
			this.s3Client = null;
		} else {
			this.s3Client = S3Client.builder()
					.region(Region.US_EAST_1)
					.endpointOverride(URI.create(endpoint))
					.credentialsProvider(
							StaticCredentialsProvider.create(
									AwsBasicCredentials.create(accessKeyId, secretAccessKey)
							)
					)
					.build();
		}
	}

	public String uploadFile(byte[] fileBytes, String originalFilename) {
		if (s3Client == null) {
			throw new IllegalStateException("R2 client not configured. Set R2 environment variables.");
		}

		String ext = "";
		if (originalFilename != null && originalFilename.contains(".")) {
			ext = originalFilename.substring(originalFilename.lastIndexOf('.'));
		}
		String key = UUID.randomUUID() + ext;

		PutObjectRequest request = PutObjectRequest.builder()
				.bucket(bucketName)
				.key(key)
				.build();

		s3Client.putObject(request, RequestBody.fromBytes(fileBytes));

		return r2Endpoint + "/" + bucketName + "/" + key;
	}
}

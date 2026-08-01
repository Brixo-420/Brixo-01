package com.BRIXO.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Almacenamiento de archivos en Cloudflare R2 (API compatible con S3).
 *
 * Guarda y devuelve la <b>clave</b> del objeto (no una URL): el bucket es privado,
 * asi que los archivos se sirven leyendolos desde aca, no por enlace directo.
 */
@Service
public class R2FileService {

	private static final Logger log = LoggerFactory.getLogger(R2FileService.class);

	private final S3Client s3Client;
	private final String bucketName;
	private final String localPath;

	public R2FileService(
			@Value("${r2.access-key-id:${R2_ACCESS_KEY_ID:}}") String accessKeyId,
			@Value("${r2.secret-access-key:${R2_SECRET_ACCESS_KEY:}}") String secretAccessKey,
			@Value("${r2.endpoint:${R2_ENDPOINT:}}") String endpoint,
			@Value("${r2.bucket-name:${R2_BUCKET_NAME:brixo-uploads}}") String bucketName,
			@Value("${file.upload.path:uploads/identidad}") String localPath) {

		this.bucketName = bucketName;
		this.localPath = localPath;

		if (accessKeyId.isBlank() || secretAccessKey.isBlank() || endpoint.isBlank()) {
			this.s3Client = null;
			log.warn("R2 no configurado (faltan R2_ACCESS_KEY_ID / R2_SECRET_ACCESS_KEY / R2_ENDPOINT). "
					+ "Los archivos se guardaran en disco local.");
		} else {
			this.s3Client = S3Client.builder()
					.region(Region.US_EAST_1) // R2 ignora la region, pero el SDK la exige
					.endpointOverride(URI.create(endpoint))
					.credentialsProvider(StaticCredentialsProvider.create(
							AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
					.build();
			log.info("R2 configurado — bucket '{}'", bucketName);
		}
	}

	public boolean isEnabled() {
		return s3Client != null;
	}

	/**
	 * Sube el archivo y devuelve la clave con la que se guardo.
	 * Esa clave es lo que se persiste en BD y lo que espera {@link #download(String)}.
	 */
	public String upload(byte[] contenido, String nombreOriginal, String prefijo) throws IOException {
		String ext = "";
		if (nombreOriginal != null && nombreOriginal.contains(".")) {
			ext = nombreOriginal.substring(nombreOriginal.lastIndexOf('.'));
		}
		String key = prefijo + "_" + UUID.randomUUID() + ext;

		if (s3Client == null) {
			// Desarrollo local sin credenciales: se guarda en disco y se devuelve la ruta
			Path dir = Paths.get(localPath).toAbsolutePath();
			Files.createDirectories(dir);
			Path destino = dir.resolve(key);
			Files.write(destino, contenido);
			return destino.toString();
		}

		s3Client.putObject(
				PutObjectRequest.builder().bucket(bucketName).key(key).build(),
				RequestBody.fromBytes(contenido));

		log.info("Subido a R2: {} ({} bytes)", key, contenido.length);
		return key;
	}

	public byte[] download(String key) throws IOException {
		if (s3Client == null) {
			throw new IllegalStateException("R2 no esta configurado");
		}
		ResponseBytes<GetObjectResponse> objeto = s3Client.getObjectAsBytes(
				GetObjectRequest.builder().bucket(bucketName).key(key).build());
		return objeto.asByteArray();
	}

	/**
	 * Distingue una clave de R2 de una ruta de disco de los registros antiguos
	 * (anteriores a la migracion), que se guardaban como ruta absoluta.
	 */
	public boolean esClaveR2(String valor) {
		if (valor == null || valor.isBlank()) return false;
		return !valor.contains("/") && !valor.contains("\\");
	}
}

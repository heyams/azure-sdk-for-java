// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.spring.autoconfigure.storage.resource;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.options.BlockBlobOutputStreamOptions;
import com.azure.storage.blob.specialized.BlockBlobClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.WritableResource;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.net.URL;
import org.springframework.util.StringUtils;

/**
 * Implements {@link WritableResource} for reading and writing objects in Azure
 * StorageAccount blob. An instance of this class represents a handle to a blob.
 *
 * @author Warren Zhu
 */
public class BlobStorageResource extends AzureStorageResource {
    private static final Logger LOG = LoggerFactory.getLogger(BlobStorageResource.class);
    private static final String MSG_FAIL_GET = "Failed to get blob or container";
    private static final String MSG_FAIL_OPEN_OUTPUT = "Failed to open output stream of cloud blob";
    private static final String MSG_FAIL_CHECK_EXIST = "Failed to check existence of blob or container";
    private static final String MSG_FAIL_OPEN_INPUT = "Failed to open input stream of blob";
    private final BlobServiceClient blobServiceClient;
    private final String location;
    private final BlobContainerClient blobContainerClient;
    private final BlockBlobClient blockBlobClient;
    private final boolean autoCreateFiles;
    private final String contentType;

    public BlobStorageResource(BlobServiceClient blobServiceClient, String location) {
        this(blobServiceClient, location, false);
    }

    public BlobStorageResource(BlobServiceClient blobServiceClient, String location, Boolean autoCreateFiles) {
        this(blobServiceClient, location, autoCreateFiles, null);
    }

    public BlobStorageResource(BlobServiceClient blobServiceClient, String location, Boolean autoCreateFiles,
                               String contentType) {
        assertIsAzureStorageLocation(location);
        this.autoCreateFiles = autoCreateFiles != null && autoCreateFiles;
        this.blobServiceClient = blobServiceClient;
        this.location = location;
        this.contentType = StringUtils.hasText(contentType) ? contentType : getContentType(location);
        this.blobContainerClient = blobServiceClient.getBlobContainerClient(getContainerName(location));
        this.blockBlobClient = blobContainerClient.getBlobClient(getFilename(location)).getBlockBlobClient();
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        try {
            if (!exists()) {
                if (autoCreateFiles) {
                    create();
                } else {
                    throw new FileNotFoundException("The blob was not found: " + this.location);
                }
            }
            BlockBlobOutputStreamOptions options = new BlockBlobOutputStreamOptions();
            if (StringUtils.hasText(contentType)) {
                BlobHttpHeaders blobHttpHeaders = new BlobHttpHeaders();
                blobHttpHeaders.setContentType(contentType);
                options.setHeaders(blobHttpHeaders);
            }
            return this.blockBlobClient.getBlobOutputStream(options);
        } catch (BlobStorageException e) {
            LOG.error(MSG_FAIL_OPEN_OUTPUT, e);
            throw new IOException(MSG_FAIL_OPEN_OUTPUT, e);
        }
    }

    @Override
    public boolean exists() {
        return this.blobContainerClient.exists() && blockBlobClient.exists();
    }

    @Override
    public URL getURL() throws IOException {
        return new URL(this.blockBlobClient.getBlobUrl());
    }

    @Override
    public File getFile() throws IOException {
        throw new UnsupportedOperationException(getDescription() + " cannot be resolved to absolute file path");
    }

    @Override
    public long contentLength() throws IOException {
        return this.blockBlobClient.getProperties().getBlobSize();
    }

    @Override
    public long lastModified() throws IOException {
        return this.blockBlobClient.getProperties().getLastModified().toEpochSecond();
    }

    @Override
    public Resource createRelative(String relativePath) throws IOException {
        String newLocation = this.location + "/" + relativePath;
        return new BlobStorageResource(this.blobServiceClient, newLocation, autoCreateFiles);
    }

    @Override
    public String getFilename() {
        return this.blockBlobClient.getBlobName();
    }

    @Override
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("Azure storage account blob resource [container='")
            .append(this.blockBlobClient.getContainerName())
            .append("', blob='")
            .append(blockBlobClient.getBlobName())
            .append("'");
        sb.append("]");
        return sb.toString();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        try {
            assertExisted();
            return this.blockBlobClient.openInputStream();
        } catch (BlobStorageException e) {
            LOG.error(MSG_FAIL_OPEN_INPUT, e);
            throw new IOException(MSG_FAIL_OPEN_INPUT);
        }
    }

    @Override
    StorageType getStorageType() {
        return StorageType.BLOB;
    }

    private void assertExisted() throws FileNotFoundException {
        if (!exists()) {
            throw new FileNotFoundException("Blob or container not existed.");
        }
    }

    private void create() {
        if (!this.blobContainerClient.exists()) {
            LOG.debug("Blob container {} doesn't exist, now creating it",
                blobContainerClient.getBlobContainerName());
            this.blobContainerClient.create();
        }
    }
}

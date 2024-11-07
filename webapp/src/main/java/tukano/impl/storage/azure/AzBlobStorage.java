package tukano.impl.storage.azure;

import static tukano.api.Result.ErrorCode.BAD_REQUEST;
import static tukano.api.Result.ErrorCode.INTERNAL_ERROR;
import static tukano.api.Result.error;
import static tukano.api.Result.ok;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import java.util.function.Consumer;
import tukano.api.Result;
import tukano.impl.storage.BlobStorage;

public class AzBlobStorage implements BlobStorage {
    private final BlobContainerClient containerClient;

    private static final String BLOBS_CONTAINER_NAME = "blobs";
    private static final String storageConnectionString =
        System.getProperty("BlobStoreConnection");

    public AzBlobStorage() {
        this.containerClient = new BlobContainerClientBuilder()
                                   .connectionString(storageConnectionString)
                                   .containerName(BLOBS_CONTAINER_NAME)
                                   .buildClient();
    }

    @Override
    public Result<Void> write(String path, byte[] bytes) {
        if (path == null)
            return error(BAD_REQUEST);

        try {
            BinaryData data = BinaryData.fromBytes(bytes);

            // Get client to blob
            BlobClient blob = containerClient.getBlobClient(path);

            // Upload contents from BinaryData (check documentation for other
            // alternatives)
            blob.upload(data);

            System.out.println("File uploaded : " + path);

        } catch (Exception e) {
            e.printStackTrace();
            return error(INTERNAL_ERROR);
        }

        return ok();
    }

    @Override
    public Result<byte[]> read(String path) {
        if (path == null)
            return error(BAD_REQUEST);

        try {
            // Get client to blob
            BlobClient blob = containerClient.getBlobClient(path);

            // Download contents to BinaryData (check documentation for other
            // alternatives)
            BinaryData data = blob.downloadContent();

            byte[] bytes = data.toBytes();

            System.out.println("Blob size : " + bytes.length);
            return bytes != null ? ok(bytes) : error(INTERNAL_ERROR);
        } catch (Exception e) {
            e.printStackTrace();
            return error(INTERNAL_ERROR);
        }
    }

    @Override
    public Result<Void> read(String path, Consumer<byte[]> sink) {
        if (path == null)
            return error(BAD_REQUEST);

        // we don't write to sink anymore

        // try {
        // // Get client to blob
        // BlobClient blob = containerClient.getBlobClient(path);
        //
        // // Download contents to BinaryData (check documentation for other
        // // alternatives)
        // BinaryData data = blob.downloadContent();
        //
        // byte[] bytes = data.toBytes();
        //
        // System.out.println("Blob size : " + bytes.length);
        // return bytes != null ? ok(bytes) : error(INTERNAL_ERROR);
        // } catch (Exception e) {
        // e.printStackTrace();
        // return error(INTERNAL_ERROR);
        // }
        // IO.read(file, CHUNK_SIZE, sink);
        return ok();
    }

    @Override
    public Result<Void> delete(String path) {
        if (path == null)
            return error(BAD_REQUEST);

        try {
            // Get client to blob
            BlobClient blob = containerClient.getBlobClient(path);

            blob.delete();

            return ok();
        } catch (Exception e) {
            e.printStackTrace();
            return error(INTERNAL_ERROR);
        }
    }

    @Override
    public Result<Void> deleteAll(String userId) {
        if (userId == null)
            return error(BAD_REQUEST);

        try {
            containerClient.listBlobs().forEach(blob -> {
                String blobName = blob.getName();
                if(blobName.contains(userId)) {
                    // Would this be preferable over an  actual deletion?
                    // blob.setDeleted(true);

                    BlobClient bc = containerClient.getBlobClient(blobName);
                    bc.delete();
                }
            });

            return ok();
        } catch (Exception e) {
            e.printStackTrace();
            return error(INTERNAL_ERROR);
        }
    }
}

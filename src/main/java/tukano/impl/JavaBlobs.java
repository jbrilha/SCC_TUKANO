package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.ErrorCode.FORBIDDEN;
import static tukano.api.Result.error;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.logging.Logger;
import tukano.api.Blobs;
import tukano.api.Result;
import tukano.impl.storage.BlobStorage;
import tukano.impl.storage.FilesystemStorage;
import tukano.impl.storage.azure.AzBlobStorage;
import utils.Hash;
import utils.Hex;

public class JavaBlobs implements Blobs {
    private static Blobs instance;
    private static Logger Log = Logger.getLogger(JavaBlobs.class.getName());
    private static String triggerFunctionEndpoint =
        System.getProperty("BLOBS_TRIGGER_FUNC_URL");

    public String baseURI;
    private AzBlobStorage azStorage;

    synchronized public static Blobs getInstance() {
        if (instance == null)
            instance = new JavaBlobs();
        return instance;
    }

    private JavaBlobs() {
        azStorage = new AzBlobStorage();
        baseURI = String.format("%s/%s/", Blobs.STORAGE_ENDPOINT, Blobs.NAME);
    }

    @Override
    public Result<Void> upload(String blobId, byte[] bytes, String token) {
        Log.info(
            ()
                -> format("upload : blobId = %s, sha256 = %s, token = %s\n",
                          blobId, Hex.of(Hash.sha256(bytes)), token));

        if (!validBlobId(blobId, token))
            return error(FORBIDDEN);

        return azStorage.write(blobId, bytes);
    }

    @Override
    public Result<byte[]> download(String blobId, String token) {
        Log.info(
            () -> format("download : blobId = %s, token=%s\n", blobId, token));

        if (!validBlobId(blobId, token))
            return error(FORBIDDEN);

        triggerFunction(blobId);

        return azStorage.read(blobId);
    }

    private void triggerFunction(String blobId) {
        HttpClient.newHttpClient().sendAsync(
            HttpRequest.newBuilder()
                .uri(URI.create(triggerFunctionEndpoint + blobId))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.discarding());
    }

    @Override
    public Result<Void> delete(String blobId, String token) {
        Log.info(
            () -> format("delete : blobId = %s, token=%s\n", blobId, token));

        if (!validBlobId(blobId, token))
            return error(FORBIDDEN);

        return azStorage.delete(blobId);
    }

    @Override
    public Result<Void> deleteAllBlobs(String userId, String token) {
        Log.info(()
                     -> format("deleteAllBlobs : userId = %s, token=%s\n",
                               userId, token));

        if (!Token.isValid(token, userId))
            return error(FORBIDDEN);

        return azStorage.deleteAll(userId);
    }

    private boolean validBlobId(String blobId, String token) {
        return Token.isValid(token, toURL(blobId));
    }

    private String toPath(String blobId) { return blobId.replace("+", "/"); }

    private String toURL(String blobId) { return baseURI + blobId; }
}

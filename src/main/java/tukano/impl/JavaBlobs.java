package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.ErrorCode.FORBIDDEN;
import static tukano.api.Result.error;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.Consumer;
import java.util.logging.Logger;
import tukano.api.Blobs;
import tukano.api.Result;
import tukano.impl.rest.TukanoRestServer;
import tukano.impl.storage.BlobStorage;
import tukano.impl.storage.FilesystemStorage;
import tukano.impl.storage.azure.AzBlobStorage;
import utils.Hash;
import utils.Hex;

public class JavaBlobs implements Blobs {
    private static Blobs instance;
    private static Logger Log = Logger.getLogger(JavaBlobs.class.getName());

    public String baseURI;
    private BlobStorage storage;
    private AzBlobStorage azStorage;

    synchronized public static Blobs getInstance() {
        if (instance == null)
            instance = new JavaBlobs();
        return instance;
    }

    private JavaBlobs() {
        storage = new FilesystemStorage();
        azStorage = new AzBlobStorage();
        baseURI =
            String.format("%s/%s/", TukanoRestServer.serverURI, Blobs.NAME);
    }

    @Override
    public Result<Void> upload(String blobId, byte[] bytes, String token) {
        Log.info(
            ()
                -> format("upload : blobId = %s, sha256 = %s, token = %s\n",
                          blobId, Hex.of(Hash.sha256(bytes)), token));

        if (!validBlobId(blobId, token))
            return error(FORBIDDEN);

        // TODO move "toPath" call to write method of FSStorage
        // to allow for sending only yhe blobId to that method so that the
        // interface works the same for CosmosDB
        // return storage.write(toPath(blobId), bytes);

        return azStorage.write(blobId, bytes);
    }

    private void triggerFunction(String blobId) {
        System.out.println("\n\nTriggering function for: " + blobId);
        HttpClient.newHttpClient().sendAsync(
            HttpRequest.newBuilder()
                .uri(URI.create(
                    "https://fun70274northeurope.azurewebsites.net/tukano/blobs/" +
                    blobId))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.discarding());
        System.out.println("Triggered function for: " + blobId + "\n\n");
    }

    @Override
    public Result<byte[]> download(String blobId, String token) {
        Log.info(
            () -> format("download : blobId = %s, token=%s\n", blobId, token));

        if (!validBlobId(blobId, token))
            return error(FORBIDDEN);

        triggerFunction(blobId);

        return azStorage.read(blobId);
        // return storage.read(toPath(blobId));
    }

    /*
     * @Override
     * // TODO This is completely ignorable I think?
     * public Result<Void> downloadToSink(String blobId, Consumer<byte[]> sink,
     * String token) {
     * Log.info(()
     * -> format("downloadToSink : blobId = %s, token = %s\n",
     * blobId, token));
     *
     * if (!validBlobId(blobId, token))
     * return error(FORBIDDEN);
     *
     * return storage.read(toPath(blobId), sink);
     * }
     */

    @Override
    public Result<Void> delete(String blobId, String token) {
        Log.info(
            () -> format("delete : blobId = %s, token=%s\n", blobId, token));

        if (!validBlobId(blobId, token))
            return error(FORBIDDEN);

        // return storage.delete(toPath(blobId));
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

package test;

import java.io.File;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.Random;

import tukano.api.Result;
import tukano.api.User;
import tukano.api.UserDAO;
import tukano.clients.rest.RestBlobsClient;
import tukano.clients.rest.RestShortsClient;
import tukano.clients.rest.RestUsersClient;
import tukano.impl.cache.RedisCache;
import tukano.impl.rest.TukanoRestServer;
import utils.JSON;

public class Test {

    static {
        System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s");
    }

    public static void main(String[] args) throws Exception {
        new Thread(() -> {
            try {
                TukanoRestServer.main(new String[] {});
            } catch (Exception x) {
                x.printStackTrace();
            }
        }).start();

        Thread.sleep(1000);

        var serverURI = String.format("http://localhost:%s/rest", TukanoRestServer.PORT);

        var blobs = new RestBlobsClient(serverURI);
        var users = new RestUsersClient(serverURI);
        var shorts = new RestShortsClient(serverURI);

        // TESTED
        show(users.createUser(new User("wales", "12345", "jimmy@wikipedia.pt", "Jimmy Wales")));
        show(users.createUser(new User("liskov", "54321", "liskov@mit.edu", "Barbara Liskov")));

        // TESTED
        show(users.updateUser("wales", "12345", new User("wales", "12345", "jimmy@wikipedia.com", "")));
        show(users.getUser("wales", "12345"));

        // TESTED and with query
        show(users.searchUsers(""));

        final String MOST_RECENT_USERS_LIST = "MostRecentUsers";
        final String MUM_USERS_COUNTER = "NumUsers";

        Result<tukano.api.Short> s1, s2;

        // TESTED
        show(s2 = shorts.createShort("liskov", "54321"));
        show(s1 = shorts.createShort("wales", "12345"));
        show(shorts.createShort("wales", "12345"));
        show(shorts.createShort("wales", "12345"));
        show(shorts.createShort("wales", "12345"));

        // NOT TESTED
        var blobUrl = URI.create(s2.value().getBlobUrl());
        System.out.println("------->" + blobUrl);

        var blobId = new File(blobUrl.getPath()).getName();
        System.out.println("BlobID:" + blobId);

        var token = blobUrl.getQuery().split("=")[1];

        blobs.upload(blobUrl.toString(), randomBytes(100), token);

        var s2id = s2.value().getShortId();

        show(shorts.follow("liskov", "wales", true, "54321"));
        show(shorts.followers("wales", "12345"));

        show(shorts.like(s2id, "liskov", true, "54321"));
        show(shorts.like(s2id, "liskov", true, "54321"));
        show(shorts.likes(s2id, "54321"));
        show(shorts.getFeed("liskov", "12345"));
        show(shorts.getShort(s2id));

        show(shorts.getShorts("wales"));

        show(shorts.followers("wales", "12345"));

        show(shorts.getFeed("liskov", "12345"));

        show(shorts.getShort(s2id));
        //
        //
        // blobs.forEach( b -> {
        // var r = b.download(blobId);
        // System.out.println( Hex.of(Hash.sha256( bytes )) + "-->" +
        // Hex.of(Hash.sha256( r.value() )));
        //
        // });

        try {
            Locale.setDefault(Locale.US);

            var id1 = "john-" + System.currentTimeMillis();
            var user1 = new User(id1, "12345", "john@nova.pt", "John Smith");

            try (var jedis = RedisCache.getCachePool().getResource()) {

                var key = "user:" + user1.getId();
                var value = JSON.encode(user1);

                jedis.set(key, value);

                jedis.expire(key, 3000);

                var user2 = JSON.decode(jedis.get(key), User.class);
                System.out.println(user2);

                var user3 = JSON.decode(jedis.get(key), UserDAO.class);
                System.out.println(user3);

                var cnt = jedis.lpush(MOST_RECENT_USERS_LIST, value);
                if (cnt > 5)
                    jedis.ltrim(MOST_RECENT_USERS_LIST, 0, 4);

                var list = jedis.lrange(MOST_RECENT_USERS_LIST, 0, -1);

                System.out.println(MOST_RECENT_USERS_LIST);

                for (String s : list)
                    System.out.println(JSON.decode(s, User.class));

                cnt = jedis.incr(MUM_USERS_COUNTER);
                System.out.println("Num users : " + cnt);

            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        show(users.deleteUser("wales", "12345"));


        System.exit(0);
    }

    private static Result<?> show(Result<?> res) {
        if (res.isOK())
            System.err.println("OK: " + res.value());
        else
            System.err.println("ERROR:" + res.error());
        return res;

    }

    private static byte[] randomBytes(int size) {
        var r = new Random(1L);

        var bb = ByteBuffer.allocate(size);

        r.ints(size).forEach(i -> bb.put((byte) (i & 0xFF)));

        return bb.array();

    }
}

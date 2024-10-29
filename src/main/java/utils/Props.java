package utils;

import java.io.InputStreamReader;
import java.util.Properties;

public class Props {

    public static String get(String key, String defaultValue) {
        var val = System.getProperty(key);
        return val == null ? defaultValue : val;
    }

    public static <T> T get(String key, Class<T> clazz) {
        var val = System.getProperty(key);
        if (val == null)
            return null;
        return JSON.decode(val, clazz);
    }

    public static void load(String resourceFile) {

        System.out.println("\n\n\nPROPS:\n");
        try (var in = Props.class.getClassLoader().getResourceAsStream(
                resourceFile)) {
            var reader = new InputStreamReader(in);
            var props = new Properties();
            props.load(reader);
            props.forEach((k, v) -> {
                System.out.println(k.toString() + " | " + v.toString());
                System.setProperty(k.toString(), v.toString());
            });
            System.getenv().forEach(System::setProperty);
        System.out.println("\n\n\n");
        } catch (Exception x) {
            System.out.println("\n\n\nFUCKING PROPS UUUUUUUUUU\n\n\n");
            x.printStackTrace();
        }
    }
}

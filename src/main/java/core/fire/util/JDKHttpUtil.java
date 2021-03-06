/**
 * 
 */
package core.fire.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * @author lhl
 *
 *         2015年12月30日 下午2:41:08
 */
final public class JDKHttpUtil
{
    public static final int BUFFER_SIZE = 1024;

    private JDKHttpUtil() {
    }

    private static final int HTTP_TIMEOUT_MS = 1000 * 20; // 连接或者读取超时

    /**
     * 发送HTTP GET请求
     * 
     * @param getUrl 请求url，附带get参数，注意参数是否需要URLEncode由调用者决定
     * @return 应答消息
     */
    public static String GET(String getUrl) {
        Objects.requireNonNull(getUrl, "GET: getUrl 不能为空");

        try {
            URL url = new URL(getUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(HTTP_TIMEOUT_MS);
            conn.setReadTimeout(HTTP_TIMEOUT_MS);
            conn.connect();

            try (InputStream in = conn.getInputStream()) {
                return copyToString(in);
            } catch (IOException ioe) {
                // 服务端返回404等错误代码将导致conn.getInputStream()抛出异常，此时可以使用conn.getErrorStream()获取输出
                try (InputStream errorStream = conn.getErrorStream()) {
                    return copyToString(errorStream);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 发送HTTP POST请求，以(application/x-www-form-urlencoded)形式发送数据
     * 
     * @param postUrl 请求url
     * @param param post数据
     * @return 应答，waitForReturn为false时直接返回null
     */
    public static String POST(String postUrl, byte[] param) {
        Objects.requireNonNull(postUrl, "POST: postUrl 不能为空");
        Objects.requireNonNull(param, "POST: param 不能为空");

        try {
            URL url = new URL(postUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setConnectTimeout(HTTP_TIMEOUT_MS);
            conn.setReadTimeout(HTTP_TIMEOUT_MS);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("Content-Length", String.valueOf(param.length));

            if (param.length > 0) {
                try (OutputStream out = conn.getOutputStream()) {
                    out.write(param);
                    out.flush();
                }
            }

            try (InputStream in = conn.getInputStream()) {
                return copyToString(in);
            } catch (IOException ioe) {
                // 服务端返回404等错误代码将导致conn.getInputStream()抛出异常，此时可以使用conn.getErrorStream()获取输出
                try (InputStream errorStream = conn.getErrorStream()) {
                    return copyToString(errorStream);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 将输入流内容转换为字符串
     * 
     * @param in
     * @return
     * @throws IOException
     */
    private static String copyToString(InputStream in) throws IOException {
        StringBuilder out = new StringBuilder();
        InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
        char[] buffer = new char[BUFFER_SIZE];
        int bytesRead = -1;
        while ((bytesRead = reader.read(buffer)) != -1) {
            out.append(buffer, 0, bytesRead);
        }
        return out.toString();
    }
}

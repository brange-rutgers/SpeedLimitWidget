package edu.rutgers.brange.speedlimitwidget;

import android.widget.Toast;

import org.junit.Test;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {
    @Test
    public void addition_isCorrect() {
        assertEquals(4, 2 + 2);
    }

    private static String readStream(InputStream is) {
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            int i = is.read();
            while (i != -1) {
                bo.write(i);
                i = is.read();
            }
            return bo.toString();
        } catch (IOException e) {
            return "";
        }
    }

    @Test
    public void http_isValid() {


        String urlString = "http://www.overpass-api.de/api/xapi?*[maxspeed=*][bbox=5.6283473,50.5348043,5.6285261,50.534884]";
        String response = "";
        try {
            URL url = new URL("http://www.android.com/");
            url = new URL(urlString);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            try {
                InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                response = readStream(in);
            } finally {
                urlConnection.disconnect();
            }
            assertNotEquals("", response);
        } catch (Exception e) {
            assertEquals(true, false);
        }
    }
}
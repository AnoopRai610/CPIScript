package com.cpi;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class HTTPClient {

	public static String externalCall(String targetUrl, Map<String, String> headers, String method,
			String requestcontent, Proxy proxy) throws Exception {
		return new String(HTTPClient.externalCallByteArray(targetUrl, headers, method, requestcontent, proxy),
				StandardCharsets.UTF_8);
	}

	public static byte[] externalCallByteArray(String targetUrl, Map<String, String> headers, String method,
			String requestcontent, Proxy proxy) throws Exception {
		try {
			URL url = new URL(targetUrl);
			
			HttpURLConnection con;
			
			if (proxy != null)
				con = (HttpURLConnection) url.openConnection(proxy);
			else
				con = (HttpURLConnection) url.openConnection();
			
			con.setRequestMethod(method);
			
			headers.forEach{k, v -> con.setRequestProperty(k, v)};
			
			if (requestcontent != null && !requestcontent.isEmpty()
					&& !(method.equalsIgnoreCase("GET") || method.equalsIgnoreCase("DELETE"))) {
				con.setDoOutput(true);
				OutputStream os = con.getOutputStream();
				os.write(requestcontent.getBytes());
				os.flush();
				os.close();
			}
			
			con.setConnectTimeout(5000);
			con.setReadTimeout(5000);

			con.connect();
			int status = con.getResponseCode();
			if (status < 299) {
				InputStream inS = con.getInputStream();
				ByteArrayOutputStream buffer = new ByteArrayOutputStream();
				int nRead;
				byte[] data = new byte[16384];
				while ((nRead = inS.read(data, 0, data.length)) != -1)
					buffer.write(data, 0, nRead);
				inS.close();
				con.disconnect();
				return buffer.toByteArray();
			} else {
				BufferedReader br = new BufferedReader(new InputStreamReader((con.getErrorStream())));
				StringBuilder sb = new StringBuilder();
				String output;
				while ((output = br.readLine()) != null) {
					sb.append(output);
				}
				con.disconnect();
				throw new Exception("Error while calling" + targetUrl + "[" + status + ":(" + con.getResponseMessage()
						+ ") Message:" + sb.toString());
			}
		} catch (Exception e) {
			throw new Exception(e.getMessage() + " Error while calling" + targetUrl, e);
		}
	}
}

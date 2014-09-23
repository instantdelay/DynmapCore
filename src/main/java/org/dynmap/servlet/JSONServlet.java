package org.dynmap.servlet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.dynmap.web.HttpField;
import org.eclipse.jetty.http.HttpStatus;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

/**
 * Convenience base class for servlets that accept JSON requests and produce JSON responses.
 * 
 */
public class JSONServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	
	protected static final Logger log = Logger.getLogger("Minecraft");
	private static final JSONParser parser = new JSONParser();
	private static final Charset UTF8 = Charset.forName("UTF-8");
	
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		try {
			final JSONObject requestData = readRequest(req, resp);
			if (requestData == null)
				return;
			
			final JsonHttpResponse response = doJsonPost(requestData, req);
			if (response != null) {
				writeResponse(resp, response);
			}
			else {
				super.doPost(req, resp);
			}
			
		}
		catch (Exception ex) {
			log.log(Level.SEVERE, "Error processing POST request", ex);
			resp.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
		}
	}

	private JSONObject readRequest(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		final InputStreamReader reader = new InputStreamReader(req.getInputStream(), UTF8);

		try {
		    return (JSONObject)parser.parse(reader);
		}
		catch (Exception ex) {
			log.log(Level.INFO, "Bad JSON request", ex);
			resp.setStatus(HttpStatus.BAD_REQUEST_400);
			return null;
		}
	}
	
	@Override
	protected void doPut(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		try {
			final JSONObject requestData = readRequest(req, resp);
			if (requestData == null)
				return;
			
			final JsonHttpResponse response = doJsonPut(requestData, req);
			if (response != null) {
				writeResponse(resp, response);
			}
			else {
				super.doPost(req, resp);
			}
			
		}
		catch (Exception ex) {
			log.log(Level.SEVERE, "Error processing PUT request", ex);
			resp.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
		}
	}

	private static void writeResponse(HttpServletResponse resp, final JsonHttpResponse responseData) throws IOException {
		final String dateStr = new Date().toString();
		resp.addHeader(HttpField.Date, dateStr);
		resp.addHeader(HttpField.LastModified, dateStr);
		resp.addHeader(HttpField.Expires, "Thu, 01 Dec 1994 16:00:00 GMT");
		
		resp.setStatus(responseData.getStatusCode());
		
		if (responseData.getBody() != null) {
			final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
			final Writer writer = new OutputStreamWriter(byteStream, UTF8);
			responseData.getBody().writeJSONString(writer);
			writer.close();
			
			//application/json ?
		    resp.addHeader(HttpField.ContentType, "text/plain; charset=utf-8");
		    resp.addHeader(HttpField.ContentLength, Integer.toString(byteStream.size()));
		
		    byteStream.writeTo(resp.getOutputStream());
		}
		
		resp.flushBuffer();
	}
	
	protected JsonHttpResponse doJsonPut(JSONObject requestData, HttpServletRequest request) {
		return null;
	}
	
	protected JsonHttpResponse doJsonPost(JSONObject requestData, HttpServletRequest request) {
		return null;
	}

	public static class JsonHttpResponse {
		private final int statusCode;
		private final JSONObject body;
		
		public JsonHttpResponse(JSONObject body) {
			this.statusCode = 200;
			this.body = body;
		}
		
		public JsonHttpResponse(int statusCode) {
			this.statusCode = statusCode;
			this.body = null;
		}
		
		public JsonHttpResponse(JSONObject body, int statusCode) {
			this.statusCode = statusCode;
			this.body = body;
		}
		
		public int getStatusCode() {
			return statusCode;
		}
		
		public JSONObject getBody() {
			return body;
		}
	}
    
}

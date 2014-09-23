package org.dynmap.servlet;

import static org.dynmap.JSONUtils.s;

import java.io.IOException;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.dynmap.DynmapCore;
import org.dynmap.markers.GenericMarker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerIcon;
import org.dynmap.markers.MarkerSet;
import org.dynmap.markers.PolyLineMarker;
import org.eclipse.jetty.http.HttpStatus;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * Support for handling marker create, edit, and delete requests from the web interface.
 * 
 * <p>Currently supports:
 * <ul>
 * <li>Adding a node to a polyline
 * <li>Deleting a polyline node
 * <li>Moving a polyline node
 * </ul>
 * 
 */
public class MarkerEditServlet extends JSONServlet {
	private static final long serialVersionUID = 1L;
	
	private static final String ACTION_DELETE = "delete";
	private static final String ACTION_INSERT = "insert";
	
	protected static final Logger log = Logger.getLogger("Minecraft");
	private static final Pattern URL_PTRN = Pattern.compile("^/([^/]+)/([^/]+)");
	
	private final DynmapCore core;
	
	public MarkerEditServlet(DynmapCore core) {
		this.core = core;
	}
	
	@Override
	protected void doDelete(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		
		String[] parts = req.getPathInfo().substring(1).split("/");
		
		if (parts.length < 2 || !"sets".equals(parts[0])) {
			resp.sendError(HttpStatus.NOT_FOUND_404);
			return;
		}
		
		MarkerAPI api = core.getMarkerAPI();
		
		MarkerSet markerSet = api.getMarkerSet(parts[1]);
		
		if (markerSet == null) {
			resp.sendError(HttpStatus.NOT_FOUND_404);
			return;
		}
		
		if (parts.length == 2) {
			// TODO delete set?
			return;
		}
		
		if (parts.length == 3) {
			resp.sendError(HttpStatus.NOT_FOUND_404);
			return;
		}
		
		final String type = parts[2];
		final String markerId = parts[3];
		
		MarkerHandler handler = getHandler(type);
		if (handler == null) {
			resp.sendError(HttpStatus.BAD_REQUEST_400);
			return;
		}
		
		boolean success = handler.delete(markerSet, markerId);
		
		if (success) {
			// OK
			resp.flushBuffer();
		}
		else {
			resp.sendError(HttpStatus.NOT_FOUND_404);
		}
	}
	
	@Override
	protected JsonHttpResponse doJsonPut(JSONObject requestData, HttpServletRequest request) {
		String[] parts = request.getPathInfo().substring(1).split("/");
		
		if (parts.length < 2 || !"sets".equals(parts[0])) {
			return new JsonHttpResponse(HttpStatus.NOT_FOUND_404);
		}
		
		MarkerAPI api = core.getMarkerAPI();
		MarkerSet markerSet = api.getMarkerSet(parts[1]);
		
		if (parts.length == 2) {
			if (markerSet == null) {
				// Add marker set
				
				final String label = (String) requestData.get("label");
				api.createMarkerSet(parts[1], label, null, true);
				return new JsonHttpResponse(HttpStatus.OK_200);
			}
			else {
				// Don't currently support PUT to existing set
				return new JsonHttpResponse(HttpStatus.BAD_REQUEST_400);
			}
		}
		
		if (markerSet == null) {
			return new JsonHttpResponse(HttpStatus.NOT_FOUND_404);
		}
		
		final String type = parts[2];
		final String markerId = parts.length > 3 ? parts[3] : null;
		
		MarkerHandler handler = getHandler(type);
		if (handler == null) {
			return new JsonHttpResponse(HttpStatus.BAD_REQUEST_400);
		}
		
		handler.create(markerSet, markerId, requestData);
		
		return new JsonHttpResponse(HttpStatus.OK_200);
	}
	
	@Override
	protected JsonHttpResponse doJsonPost(JSONObject requestData, HttpServletRequest request) {
    	Matcher matcher = URL_PTRN.matcher(request.getPathInfo());
    	if (!matcher.matches()) {
    		return new JsonHttpResponse(404);
    	}
    	
    	String setId = matcher.group(1);
    	String markerId = matcher.group(2);
    	
    	log.info("Editing marker " + markerId + " in set " + setId);
        
        MarkerSet set = core.getMarkerAPI().getMarkerSet(setId);
        PolyLineMarker line = set.findPolyLineMarker(markerId);
        
        String action = (String)requestData.get("action");
        int n = ((Long)requestData.get("n")).intValue();
        
        if (ACTION_DELETE.equals(action)) {
        	if (line.getCornerCount() <= 2) {
        		log.warning("Attempt to delete corner from 2-corner line ignored.");
        	}
        	else {
        		line.deleteCorner(n);
        	}
        }
        else {
        	Double x = Double.parseDouble((String) requestData.get("x"));
        	Double y = Double.parseDouble((String) requestData.get("y"));
        	Double z = Double.parseDouble((String) requestData.get("z"));
        
	        if (ACTION_INSERT.equals(action)) {
	        	log.info("Inserting new corner: " + n + ", " + x + ", " + y + ", " + z);
	        	line.insertCorner(n, x, y, z);
	        }
	        else {
	        	log.info("Updating line coordinates: " + n + ", " + x + ", " + y + ", " + z);
	        	line.setCornerLocation(n, x, y, z);
	        }
        }
        
        JSONObject json = new JSONObject();
        s(json, "ok", "yay");
        return new JsonHttpResponse(json);
	}
	
	private MarkerHandler getHandler(String type) {
		if (type.equals("lines")) {
			return new PolyLineMarkerHandler();
		}
		else if (type.equals("points")) {
			return new PointMarkerHandler();
		}
//		else if (type.equals("area")) {
//			marker = markerSet.findAreaMarker(markerId);
//		}
//		else if (type.equals("circle")) {
//			marker = markerSet.findCircleMarker(markerId);
//		}

		return null;
	}
	
	private static interface MarkerHandler {
		boolean delete(MarkerSet set, String markerId);
		void create(MarkerSet set, String markerId, JSONObject sourceData);
	}
	
	public class PointMarkerHandler implements MarkerHandler {
		@Override
		public boolean delete(MarkerSet set, String markerId) {
			GenericMarker marker = set.findMarker(markerId);
			if (marker == null)
				return false;
			marker.deleteMarker();
			return true;
		}

		@Override
		public void create(MarkerSet set, String markerId, JSONObject sourceData) {
			final String label = (String) sourceData.get("label");
			final double x = Double.parseDouble((String) sourceData.get("x"));
			final double y = Double.parseDouble((String) sourceData.get("y"));
			final double z = Double.parseDouble((String) sourceData.get("z"));
			final String world = sourceData.containsKey("world") ? (String)sourceData.get("world") : "world";
			final String iconId = (String) sourceData.get("icon");
			
			MarkerIcon icon = null;
			if (iconId != null) {
				icon = core.getMarkerAPI().getMarkerIcon(iconId);
			}
			
			set.createMarker(markerId, label, world, x, y, z, icon, true);
		}
	}
	
	public static class PolyLineMarkerHandler implements MarkerHandler {
		@Override
		public boolean delete(MarkerSet set, String markerId) {
			GenericMarker marker = set.findPolyLineMarker(markerId);
			if (marker == null)
				return false;
			marker.deleteMarker();
			return true;
		}
		
		@Override
		public void create(MarkerSet set, String markerId, JSONObject sourceData) {
			final String label = (String) sourceData.get("label");
//			final double[] x = (double[]) sourceData.get("x");
//			final double[] y = (double[]) sourceData.get("y");
//			final double[] z = (double[]) sourceData.get("z");
			final String world = sourceData.containsKey("world") ? (String)sourceData.get("world") : "world";
			
			final JSONArray points = (JSONArray) sourceData.get("points");
			
			double[] x = new double[points.size()];
			double[] y = new double[points.size()];
			double[] z = new double[points.size()];
			
			int i = 0;
			for (Object pointObj : points) {
				JSONObject point = (JSONObject) pointObj;
				x[i] = Double.parseDouble((String) point.get("x"));
				y[i] = Double.parseDouble((String) point.get("y"));
				z[i] = Double.parseDouble((String) point.get("z"));
				i++;
			}
			
			set.createPolyLineMarker(markerId, label, false, world, x, y, z, true);
		}
	}
	
	private static class MapItemId {
		private String setId;
		private String itemType;
		private String itemId;
		public MapItemId(String setId, String itemType, String itemId) {
			super();
			this.setId = setId;
			this.itemType = itemType;
			this.itemId = itemId;
		}
		public String getSetId() {
			return setId;
		}
		public String getItemType() {
			return itemType;
		}
		public String getItemId() {
			return itemId;
		}
	}
	
}

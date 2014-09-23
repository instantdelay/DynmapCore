componentconstructors['markeredit'] = function(dynmap, configuration) {
	var me = this;

	function deleteMarker(markerSet, marker) {
		$(dynmap).trigger('component.markers', {msg: 'markerdeleted', set: markerSet.id, id: marker.id});
	}

	function beginLineCreation() {
		var points = [];

		dynmap.map.on('click', function(e) {

			var loc = dynmap.getProjection().fromLatLngToLocation(e.latlng, 65);

			points.push({x: ""+loc.x, y: ""+loc.y, z: ""+loc.z});

			if (points.length >= 2) {
				dynmap.map.off('click', arguments.callee);

				var label = prompt("Line label?");
				if (label == null) return;

				var data = {
					label: label,
					points: points,
					world: dynmap.world.name
				};

				// FIXME
				var set = {
					id: "markers"
				};

				$.putJSON(data, dynmap.options.url.edit + "/sets/" + set.id + "/lines", function(resp) {
					// Nothing to do right now... relying on update events
				}, function(status, statusMessage) {
					alert('Could not create: ' + statusMessage);
				});

				points = [];
			}

			L.DomEvent.stopPropagation(e);
		});
	}

	$.contextMenu({
		selector: '.leaflet-container',
		trigger: 'none',
		build: function($trigger, e) {
			if (me.menuTriggerMarker == null) {
				// Right-click on map
				return {
					callback: function(key, options) {
						if (key == "createMarker") {
							var label = prompt("Marker label?");
							if (label == null) return;
							var icon = prompt("Icon?");
							if (icon == null) return;
							var loc = dynmap.getProjection().fromLatLngToLocation(me.menuLocation, 65);

							var data = {
								label: label,
								x: ""+loc.x,
								y: ""+loc.y,
								z: ""+loc.z,
								world: dynmap.world.name
							};

							// FIXME
							var set = {
								id: "markers"
							};

							$.putJSON(data, dynmap.options.url.edit + "/sets/" + set.id + "/points", function(resp) {
								// Nothing to do right now... relying on update events
							}, function(status, statusMessage) {
								alert('Could not create: ' + statusMessage);
							});
						}
						else if (key == "createLine") {
							beginLineCreation();
						}
					},
					items: {
						"createMarker": {name: "Create Marker Here"},
						"createLine": {name: "Draw Line"}
					}
				};
			}
			else {
				// Right-click on a marker

				var items = {
					"edit": {name: "Edit Properties"},
					"delete": {name: "Delete"}
				};
				if (me.menuTriggerType == "lines") {
					items.editPoints = {name: "Edit Points"};
				}

				return {
					callback: function(key, options) {
						if (key == "editPoints") {
							me.menuTriggerMarker.our_layer.editing.enable();
						}
						else if (key == "delete") {
							var set = me.menuTriggerMarkerSet;
							var marker = me.menuTriggerMarker;
							var type = me.menuTriggerType;
							deleteMarker(set, marker);
							$.ajax({
								url: dynmap.options.url.edit + "/sets/" + set.id + "/" + type + "/" + marker.id,
								type: 'DELETE',
								error: function(status, statusMessage) {
									alert('Could not delete: ' + statusMessage);
								}
							});
						}
					},
					items: items
				};
			}
		}
	});

	dynmap.map.on('contextmenu', function(e) {
		me.menuTriggerMarker = null;
		me.menuLocation = e.latlng;
		$(".leaflet-container").contextMenu({
			x: e.containerPoint.x,
			y: e.containerPoint.y
		});
	});

	function lineAdded(line, markerSet) {
		line.our_layer.on('contextmenu', function(e) {
			me.menuTriggerMarker = line;
			me.menuTriggerMarkerSet = markerSet;
			me.menuTriggerType = "lines";
			$(".leaflet-container").contextMenu({
				x: e.containerPoint.x,
				y: e.containerPoint.y
			});

			// Prevent the map context menu handler
			L.DomEvent.stopPropagation(e);
		});

		var originalLatLngs = $.map(line.our_layer.getLatLngs(), function(ll) {
			return new L.LatLng(ll.lat, ll.lng);
		});

		line.our_layer.on('edit', function(e) {

			var arr = line.our_layer.getLatLngs();
			var diff = -1;

			for (var i = 0; i < Math.min(arr.length, originalLatLngs.length); i++) {
				if (! arr[i].equals(originalLatLngs[i])) {
					diff = i;
					break;
				}
			}

			var data = {};
			var action;

			if (arr.length > originalLatLngs.length) {
				if (diff == -1)
					diff = arr.length - 1;

				action = "insert";
			}
			else if (arr.length == originalLatLngs.length) {
				action = "move";
			}
			else {
				if (diff == -1)
					diff = originalLatLngs.length - 1;

				action = "delete";
			}

			data.n = diff;
			data.action = action;

			if (action == "insert" || action == "move") {
				var loc = dynmap.getProjection().fromLatLngToLocation(arr[diff], 65);
				data.x = ""+loc.x;
				data.y = ""+loc.y;
				data.z = ""+loc.z;
			}

			$.postJSON(data, dynmap.options.url.edit + "/" + markerSet.id + "/" + line.id, function(resp) {
				// Nothing to do right now... relying on update events
			}, function(status, statusMessage) {
				alert('Could not edit: ' + statusMessage);
			});

		});
	}

	function pointAdded(point, markerSet) {
		point.our_layer.on('contextmenu', function(e) {
			me.menuTriggerMarker = point;
			me.menuTriggerMarkerSet = markerSet;
			me.menuTriggerType = "points";
			$(".leaflet-container").contextMenu({
				x: e.containerPoint.x,
				y: e.containerPoint.y
			});

			// Prevent the map context menu handler
			L.DomEvent.stopPropagation(e);
		});
	}

	$(dynmap).on('markerAdded', function(e, data) {
		if (data.type == "line") {
			lineAdded(data.marker, data.markerSet);
		}
		else if (data.type == "point") {
			pointAdded(data.marker, data.markerSet);
		}

	});

};
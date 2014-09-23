package org.dynmap;

import org.dynmap.servlet.MarkerEditServlet;

public class MarkerEditComponent extends ClientComponent {

	public MarkerEditComponent(DynmapCore core, ConfigurationNode configuration) {
		super(core, configuration);
		
		core.addServlet("/edit/*", new MarkerEditServlet(core));
	}

}

/**
 * This file is part of a plugin for Goobi - a Workflow tool for the support of mass digitization.
 *
 * Visit the websites for more information.
 *          - https://goobi.io
 *          - https://www.intranda.com
 *          - https://github.com/intranda/goobi
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation, Inc., 59
 * Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 */
package de.intranda.goobi.plugins;

import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IAdministrationPlugin;
import org.goobi.production.plugin.interfaces.IPlugin;

import de.intranda.goobi.plugins.datapoller.DataPoll;
import lombok.Data;
import net.xeoh.plugins.base.annotations.PluginImplementation;

@PluginImplementation
@Data
public class DataPollerPlugin implements IAdministrationPlugin, IPlugin {

    private static final long serialVersionUID = -3574203320176810919L;

    // TODO: Duplicated name to break cyclic dependency between base and lib module
    private static final String PLUGIN_NAME = "intranda_administration_data_poller";
    private static final String GUI = "/uii/plugin_administration_dataPoller.xhtml";
    private DataPoll cp;

    /**
     * Constructor for parameter initialisation from config file
     */
    public DataPollerPlugin() {
        this.cp = new DataPoll(false);
    }

    @Override
    public PluginType getType() {
        return PluginType.Administration;
    }

    @Override
    public String getTitle() {
        return PLUGIN_NAME;
    }

    @Override
    public String getGui() {
        return GUI;
    }

}

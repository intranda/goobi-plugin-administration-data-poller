package de.intranda.goobi.plugins;

import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IAdministrationPlugin;
import org.goobi.production.plugin.interfaces.IPlugin;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.quartz.impl.StdSchedulerFactory;

import de.intranda.goobi.plugins.cataloguePoller.CataloguePoll;
import lombok.Data;
import lombok.extern.log4j.Log4j;
import net.xeoh.plugins.base.annotations.PluginImplementation;

@PluginImplementation
@Data
@Log4j
public class CataloguePollerPlugin implements IAdministrationPlugin, IPlugin {

    private static final String PLUGIN_NAME = "intranda_admin_catalogue_poller";
    private static final String GUI = "/uii/administration_cataloguePoller.xhtml";
    private CataloguePoll cp;
    
    /**
     * Constructor for parameter initialisation from config file
     */
    public CataloguePollerPlugin() {
        cp = new CataloguePoll();
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

    public void updateStatusInformation() {
        try {
            // get all job groups
            SchedulerFactory schedFact = new StdSchedulerFactory();
            Scheduler sched = schedFact.getScheduler();
            for (String groupName : sched.getJobGroupNames()) {
                // get all jobs within the group
                for (Object name : sched.getJobNames(groupName)) {
                    log.debug("Scheduler job: " + groupName + " - " + name);
                }
            }            
        } catch (SchedulerException e) {
            log.error("Error while reading job information", e);
        }
    }
    
}

package de.intranda.goobi.plugins.cataloguePoller;

import org.quartz.Job;
import org.quartz.JobExecutionContext;

import de.intranda.goobi.plugins.cataloguePoller.PollDocStruct.PullDiff;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class QuartzJob implements Job {

    // This class is part of the GUI package, if access to other plugin jar is
    // needed, it must be initialized first
    // For each call a new instance is created, class gets destroyed after
    // execute() is finished

    @Override
    public void execute(JobExecutionContext context) {

        log.debug("Execute job for rule: " + context.getJobDetail().getName() + " - " + context.getRefireCount());
        String ruleName = context.getJobDetail().getJobDataMap().getString("rule");
        CataloguePoll cp = new CataloguePoll();
        cp.execute(ruleName);
        log.debug(cp.getDifferences().size() + " processes were checked for new catalogue information.");
        int changed = 0;
        for (PullDiff pd : cp.getDifferences()) {
            if (pd.getMessages().size() > 0) {
                log.debug("Catalogue entry updated for process " + pd.getProcessTitle());
                changed++;
                for (String diff : pd.getMessages()) {
                    log.debug("Catalogue difference: " + diff);
                }
            }
        }
        log.debug(changed + " processes were updated with new catalogue information.");

        // IAdministrationPlugin plugin = (IAdministrationPlugin)
        // PluginLoader.getPluginByTitle(PluginType.Administration,
        // "intranda_admin_catalogue_poller");
        // if (plugin != null) {
        // log.debug("Plugin initialised: " + plugin.getTitle());
        // }

    }

}

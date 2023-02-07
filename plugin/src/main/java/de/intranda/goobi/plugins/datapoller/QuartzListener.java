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
package de.intranda.goobi.plugins.datapoller;

import java.util.Date;
import java.util.HashMap;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import org.apache.commons.lang.StringUtils;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.quartz.Trigger;
import org.quartz.TriggerUtils;
import org.quartz.impl.StdSchedulerFactory;

import lombok.extern.log4j.Log4j2;

// This class goes to GUI folder because it ends with *QuartzListener.class
// it must be in GUI/ or lib/ folder

@WebListener
@Log4j2
public class QuartzListener implements ServletContextListener {

    @Override
    public void contextDestroyed(ServletContextEvent arg0) {
        // stop the data poller job
        try {
            SchedulerFactory schedFact = new StdSchedulerFactory();
            Scheduler sched = schedFact.getScheduler();
            sched.deleteJob("Data Poller", "Goobi Admin Plugin");
            log.info("Scheduler for 'Data Poller' stopped");
        } catch (SchedulerException e) {
            log.error("Error while stopping the job", e);
        }
    }

    @Override
    public void contextInitialized(ServletContextEvent arg0) {
        log.info("Starting 'Data Poller' scheduler");
        ConfigHelper cHelper = new ConfigHelper();

        try {
            // get default scheduler
            SchedulerFactory schedFact = new StdSchedulerFactory();
            Scheduler sched = schedFact.getScheduler();
            HashMap<String, ConfigInfo> rules = cHelper.readConfigInfo();

            for (ConfigInfo rule : rules.values()) {
                if (!rule.isEnabled()) {
                    // skip rule if job is not active
                    // this may need more effort to deactivate already registered jobs but
                    // the same is true for schedule changes
                    continue;
                }
                String ruleName = rule.getTitle();
                // get start time, set Calendar object  / default 22:00:00
                String configuredStartTime = rule.getStartTime();

                if (StringUtils.isBlank(configuredStartTime)) {
                    log.error("No starttime found for rule {}, no job was scheduled!", ruleName);
                    continue;
                } else if (!configuredStartTime.matches("([0-1][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]")) {
                    log.error("Invalid time format found for rule {}, use hh:mm:ss. Starting at 22:00:00", ruleName);
                    configuredStartTime = "22:00:00";
                }

                // get delay between trigger / default 24h / default is specified in ConfigHelper
                int delay = rule.getDelay();

                log.info("Definition for rule {} : starting at {}, repeat every {} hour(s).", ruleName, configuredStartTime, delay);

                // configure time to start
                java.util.Calendar startTime = java.util.Calendar.getInstance();
                startTime.set(java.util.Calendar.HOUR_OF_DAY, Integer.parseInt(configuredStartTime.substring(0, 2)));
                startTime.set(java.util.Calendar.MINUTE, Integer.parseInt(configuredStartTime.substring(3, 5)));
                startTime.set(java.util.Calendar.SECOND, Integer.parseInt(configuredStartTime.substring(6, 8)));
                startTime.set(java.util.Calendar.MILLISECOND, 0);

                //if the startTime will be before the current time, move to next day
                if (startTime.getTime().before(new Date())) {
                    startTime.add(java.util.Calendar.DAY_OF_MONTH, 1);
                }

                // create new job only if job doesnt already exist
                if (sched.getTrigger("Data Poller", "DEFAULT") == null) {
                    JobDetail jobDetail = new JobDetail("Data Poller " + ruleName, "Goobi Admin Plugin", QuartzJob.class);
                    JobDataMap map = new JobDataMap();
                    map.put("rule", ruleName);
                    jobDetail.setJobDataMap(map);
                    Trigger trigger = TriggerUtils.makeHourlyTrigger(delay);
                    trigger.setName("Data Poller");
                    trigger.setStartTime(startTime.getTime());
                    // register job and trigger at scheduler
                    sched.scheduleJob(jobDetail, trigger);
                }

            }

        } catch (SchedulerException e) {
            log.error("Error while executing the scheduler", e);
        }
    }
}

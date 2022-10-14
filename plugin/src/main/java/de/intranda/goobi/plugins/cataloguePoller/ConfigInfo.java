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
package de.intranda.goobi.plugins.cataloguePoller;

import java.util.List;

import org.goobi.production.cli.helper.StringPair;

import lombok.Data;

@Data
public class ConfigInfo {
    private String title;
    private String filter;
    private String catalogue;
    private List<StringPair> searchFields;
    private boolean mergeRecords;
    private String filterList;
    private String fieldListMode;
    private boolean analyseSubElements;
    private String startTime;
    private String delay;
    private boolean exportUpdatedRecords;
    private String lastRun;
}
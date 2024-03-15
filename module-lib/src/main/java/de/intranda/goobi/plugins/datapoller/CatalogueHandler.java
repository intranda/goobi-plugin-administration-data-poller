package de.intranda.goobi.plugins.datapoller;

import de.unigoettingen.sub.search.opac.ConfigOpac;
import de.unigoettingen.sub.search.opac.ConfigOpacCatalogue;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang.StringUtils;
import org.goobi.production.cli.helper.StringPair;
import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.PluginLoader;
import org.goobi.production.plugin.interfaces.IOpacPlugin;
import ugh.dl.Fileformat;
import ugh.dl.Prefs;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

@Log4j2
public class CatalogueHandler {
    @Getter
    private IOpacPlugin myImportOpac = null;
    @Getter
    private ConfigOpacCatalogue coc = null;
    @Getter
    private Fileformat ffNew;

    public CatalogueHandler(String configCatlogue, List<StringPair> valueList, Prefs prefs) throws CatalogueHandlerException {
        setCatalogueConfigAndPlugin(configCatlogue);
        if ("intranda_opac_json".equals(myImportOpac.getTitle())) {
            setFileFormatJson(configCatlogue, valueList, prefs);
        } else {
            setFileFormat(configCatlogue, valueList, prefs);
        }
        // TODO throw Ex if ff is null
    }

    private void setFileFormat(String configCatalogue, List<StringPair> valueList, Prefs prefs) throws CatalogueHandlerException {
        try {
            coc = ConfigOpac.getInstance().getCatalogueByName(configCatalogue);
            myImportOpac = (IOpacPlugin) PluginLoader.getPluginByTitle(PluginType.Opac, coc.getOpacType());
            this.ffNew = myImportOpac.search(valueList.get(0).getOne(), valueList.get(0).getTwo(), coc, prefs);
        } catch (Exception ex) {
            // TODO move one up
            log.error("Exception while requesting the catalogue", ex);
            throw new CatalogueHandlerException("Exception while requesting the catalogue inside of catalogue poller plugin", ex);
            // TODO ove this out of here
            // Helper.addMessageToProcessJournal(p.getId(), LogType.DEBUG,

        }
    }

    private void setFileFormatJson(String configCatalogue, List<StringPair> valueList, Prefs prefs) throws CatalogueHandlerException {
        try {
            Class<? extends Object> opacClass = this.myImportOpac.getClass();
            Method getConfigForOpac = opacClass.getMethod("getConfigForOpac");
            Object jsonOpacConfig = getConfigForOpac.invoke(this.myImportOpac);

            Class<? extends Object> jsonOpacConfigClass = jsonOpacConfig.getClass();

            Method getFieldList = jsonOpacConfigClass.getMethod("getFieldList");

            Object fieldList = getFieldList.invoke(jsonOpacConfig);
            @SuppressWarnings("unchecked")
            List<Object> fields = (List<Object>) fieldList;
            for (StringPair sp : valueList) {
                for (Object searchField : fields) {
                    Class<? extends Object> searchFieldClass = searchField.getClass();

                    Method getId = searchFieldClass.getMethod("getId");

                    Method setText = searchFieldClass.getMethod("setText", String.class);
                    Method setSelectedField = searchFieldClass.getMethod("setSelectedField", String.class);

                    Object id = getId.invoke(searchField);
                    if (((String) id).equals(sp.getOne())) {
                        String value = sp.getTwo();
                        if (StringUtils.isNotBlank(value)) {
                            setText.invoke(searchField, value);
                            setSelectedField.invoke(searchField, sp.getOne());
                        }
                    }
                }

            }
            Method search = opacClass.getMethod("search", String.class, String.class, ConfigOpacCatalogue.class, Prefs.class);

            this.ffNew = (Fileformat) search.invoke(myImportOpac, "", "", coc, prefs);
        } catch (NoSuchMethodException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            throw new CatalogueHandlerException("Exception while requesting the catalogue inside of catalogue poller plugin", ex);
            //TODO for once actually handle this exception
        }
    }

    private void setCatalogueConfigAndPlugin(String configCatalogue) {
        for (ConfigOpacCatalogue configOpacCatalogue : ConfigOpac.getInstance().getAllCatalogues("")) {
            if (configOpacCatalogue.getTitle().equals(configCatalogue)) {
                this.myImportOpac = configOpacCatalogue.getOpacPlugin();
                this.coc = configOpacCatalogue;
            }
        }
    }
}

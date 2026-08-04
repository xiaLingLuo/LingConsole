/*
 * LingConsole - A Server WebUI control panel
 * Copyright (C) 2026  XIAZHIRUI HUANG
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package im.xz.cn.lingconsole.app.web;

import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.FileTemplateResolver;
import org.thymeleaf.templateresolver.ITemplateResolver;

import java.util.Map;


public class ThymeleafRenderer {

    private final TemplateEngine engine;

    public ThymeleafRenderer(String externalTemplateDir) {
        this.engine = new TemplateEngine();
        FileTemplateResolver fileResolver = new FileTemplateResolver();
        fileResolver.setPrefix(externalTemplateDir + "/");
        fileResolver.setSuffix(".html");
        fileResolver.setTemplateMode(org.thymeleaf.templatemode.TemplateMode.HTML);
        fileResolver.setCharacterEncoding("UTF-8");
        fileResolver.setCacheable(false);
        fileResolver.setCheckExistence(true);
        ClassLoaderTemplateResolver classResolver = new ClassLoaderTemplateResolver();
        classResolver.setPrefix("/templates/");
        classResolver.setSuffix(".html");
        classResolver.setTemplateMode(org.thymeleaf.templatemode.TemplateMode.HTML);
        classResolver.setCharacterEncoding("UTF-8");
        classResolver.setCacheable(false);
        engine.addTemplateResolver(fileResolver);
        engine.addTemplateResolver(classResolver);
    }

    public String render(String templateName, Map<String, Object> model) {
        Context context = new Context();
        context.setVariables(model);
        return engine.process(templateName, context);
    }

    public String render(String templateName) {
        return render(templateName, Map.of());
    }
}

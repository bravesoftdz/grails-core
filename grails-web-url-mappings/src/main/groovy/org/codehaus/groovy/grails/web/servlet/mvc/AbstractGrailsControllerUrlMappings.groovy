/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codehaus.groovy.grails.web.servlet.mvc

import grails.util.GrailsNameUtils
import groovy.transform.Canonical
import groovy.transform.CompileStatic
import org.codehaus.groovy.grails.commons.ControllerArtefactHandler
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.commons.GrailsClass
import org.codehaus.groovy.grails.commons.GrailsControllerClass
import org.codehaus.groovy.grails.web.mapping.UrlCreator
import org.codehaus.groovy.grails.web.mapping.UrlMapping
import org.codehaus.groovy.grails.web.mapping.UrlMappingInfo
import org.codehaus.groovy.grails.web.mapping.UrlMappings
import org.codehaus.groovy.grails.web.mapping.UrlMappingsHolder
import org.springframework.http.HttpMethod

import java.util.concurrent.ConcurrentHashMap

/**
 * A {@link UrlMappingsHolder} implementation that matches URLs directly onto controller instances
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
abstract class AbstractGrailsControllerUrlMappings implements UrlMappings{

    UrlMappings urlMappingsHolderDelegate
    Map<ControllerKey, GrailsControllerClass> mappingsToGrailsControllerMap = new ConcurrentHashMap<>()

    AbstractGrailsControllerUrlMappings(GrailsApplication grailsApplication, UrlMappings urlMappingsHolderDelegate) {
        this.urlMappingsHolderDelegate = urlMappingsHolderDelegate

        def controllerArtefacts = grailsApplication.getArtefacts(ControllerArtefactHandler.TYPE)
        for(GrailsClass gc in controllerArtefacts) {
            registerController((GrailsControllerClass)gc)
        }
    }

    Collection<UrlMapping> addMappings(Closure mappings) {
        urlMappingsHolderDelegate.addMappings(mappings)
    }

    Set<HttpMethod> allowedMethods(String uri) {
        urlMappingsHolderDelegate.allowedMethods(uri)
    }

    @Override
    List getExcludePatterns() {
        urlMappingsHolderDelegate.getExcludePatterns()
    }

    @Override
    UrlCreator getReverseMapping(String controller, String action, String pluginName, Map params) {
        urlMappingsHolderDelegate.getReverseMapping(controller, action, pluginName, params)
    }

    @Override
    UrlCreator getReverseMapping(String controller, String action, String namespace, String pluginName, String httpMethod, Map params) {
        urlMappingsHolderDelegate.getReverseMapping(controller, action, namespace, pluginName, httpMethod, params)
    }

    @Override
    UrlCreator getReverseMapping(String controller, String action, String namespace, String pluginName, String httpMethod, String version, Map params) {
        urlMappingsHolderDelegate.getReverseMapping(controller, action, namespace, pluginName, httpMethod, version, params)
    }

    @Override
    UrlCreator getReverseMapping(String controller, String action, String namespace, String pluginName, Map params) {
        urlMappingsHolderDelegate.getReverseMapping(controller, action, namespace, pluginName, params)
    }

    @Override
    UrlCreator getReverseMapping(String controller, String action, Map params) {
        urlMappingsHolderDelegate.getReverseMapping(controller, action, params)
    }

    @Override
    UrlCreator getReverseMappingNoDefault(String controller, String action, Map params) {
        urlMappingsHolderDelegate.getReverseMappingNoDefault(controller, action, params)
    }

    @Override
    UrlCreator getReverseMappingNoDefault(String controller, String action, String namespace, String pluginName, String httpMethod, Map params) {
        urlMappingsHolderDelegate.getReverseMappingNoDefault(controller, action, namespace, pluginName, httpMethod, params)
    }

    @Override
    UrlCreator getReverseMappingNoDefault(String controller, String action, String namespace, String pluginName, String httpMethod, String version, Map params) {
        urlMappingsHolderDelegate.getReverseMappingNoDefault(controller, action, namespace, pluginName, httpMethod, version, params)
    }

    @Override
    UrlMappingInfo match(String uri) {
        def info = urlMappingsHolderDelegate.match(uri)
        return collectControllerMapping(info)
    }

    @Override
    UrlMappingInfo matchStatusCode(int responseCode) {
        return collectControllerMapping( urlMappingsHolderDelegate.matchStatusCode(responseCode) )
    }

    @Override
    UrlMappingInfo matchStatusCode(int responseCode, Throwable e) {
        return collectControllerMapping( urlMappingsHolderDelegate.matchStatusCode(responseCode, e) )
    }


    void registerController(GrailsControllerClass controller) {
        def namespace = controller.namespace
        def plugin = controller.pluginName
        def controllerName = controller.logicalPropertyName

        mappingsToGrailsControllerMap.put(new ControllerKey(namespace, controllerName, null, plugin), controller)

        if(plugin) {
            mappingsToGrailsControllerMap.put(new ControllerKey(namespace, controllerName, null, GrailsNameUtils.getPropertyNameForLowerCaseHyphenSeparatedName(plugin)), controller)
            def controllerKeyWithoutPlugin = new ControllerKey(namespace, controllerName, null, null)
            if(!mappingsToGrailsControllerMap.containsKey(controllerKeyWithoutPlugin)) {
                mappingsToGrailsControllerMap.put(controllerKeyWithoutPlugin, controller)
            }
        }

        for(action in controller.actions) {
            def withPluginKey = new ControllerKey(namespace, controllerName, action, plugin)
            def withoutPluginKey = new ControllerKey(namespace, controllerName, action, null)
            mappingsToGrailsControllerMap.put(withPluginKey, controller)
            if(plugin != null && !mappingsToGrailsControllerMap.containsKey(withoutPluginKey)) {
                mappingsToGrailsControllerMap.put(new ControllerKey(namespace, controllerName, action, GrailsNameUtils.getPropertyNameForLowerCaseHyphenSeparatedName(plugin)), controller)
                mappingsToGrailsControllerMap.put(withoutPluginKey, controller)
            }
        }
    }

    protected UrlMappingInfo[] collectControllerMappings(UrlMappingInfo[] infos) {
        def webRequest = GrailsWebRequest.lookup()
        infos.collect() { UrlMappingInfo info ->
            webRequest.resetParams()
            info.configure(webRequest)
            def controllerKey = new ControllerKey(info.namespace, info.controllerName, info.actionName, info.pluginName)
            GrailsControllerClass controllerClass = info ? mappingsToGrailsControllerMap.get(controllerKey) : null
            if(controllerClass) {
                return new GrailsControllerUrlMappingInfo(controllerClass, info)
            }
            else {
                return info
            }
        } as UrlMappingInfo[]
    }

    protected UrlMappingInfo collectControllerMapping(UrlMappingInfo info) {
        GrailsControllerClass  controllerClass = info ? mappingsToGrailsControllerMap.get(new ControllerKey(info.namespace, info.controllerName, info.actionName, info.pluginName)) : null

        if (controllerClass && info) {
            return new GrailsControllerUrlMappingInfo(controllerClass, info)
        } else {
            return info
        }
    }

    @Canonical
    class ControllerKey {
        String namespace
        String controller
        String action
        String plugin

        ControllerKey(String namespace, String controller, String action, String plugin) {
            this.namespace = namespace
            this.controller = controller
            this.action = action
            this.plugin = plugin
        }
    }
}


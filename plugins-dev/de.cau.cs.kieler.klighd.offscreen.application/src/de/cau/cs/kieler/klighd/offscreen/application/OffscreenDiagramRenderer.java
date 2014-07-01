/*
 * KIELER - Kiel Integrated Environment for Layout Eclipse RichClient
 *
 * http://www.informatik.uni-kiel.de/rtsys/kieler/
 * 
 * Copyright 2014 by
 * + Christian-Albrechts-University of Kiel
 *   + Department of Computer Science
 *     + Real-Time and Embedded Systems Group
 * 
 * This code is provided under the terms of the Eclipse Public License (EPL).
 * See the file epl-v10.html for the license text.
 */

package de.cau.cs.kieler.klighd.offscreen.application;

import java.io.File;
import java.io.IOException;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.XMIResource;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.swt.widgets.Display;

import de.cau.cs.kieler.klighd.LightDiagramServices;
import de.cau.cs.kieler.klighd.piccolo.export.SVGOffscreenRenderer;
import de.cau.cs.kieler.klighd.util.KlighdSynthesisProperties;

/**
 * An initial draft of an off-screen diagram rendering application generating SVG outputs.<br>
 * This application takes the names of the input files as arguments and optional switches like
 * "-consoleLog".<br> 
 * <br>
 * <b>Note:</b> On MacOS X make sure to add "-Djava.awt.headless=true" to the vmargs!
 * Otherwise the application will freeze! 
 * 
 * @author chsch
 * @author uru
 */
public class OffscreenDiagramRenderer implements IApplication {
    
    public final static String GENERATOR_SVG_FREEHEP =
            "de.cau.cs.kieler.klighd.piccolo.svggen.freeHEP";
    public final static String GENERATOR_SVG_FREEHEP_SEMANTIC =
            "de.cau.cs.kieler.klighd.piccolo.svggen.freeHEPSemantic";
    
    private final ResourceSet set = new ResourceSetImpl();

    /**
     * {@inheritDoc}
     */
    public Object start(final IApplicationContext context) throws Exception {
        final String[] appArgs =
                (String[]) context.getArguments().get(IApplicationContext.APPLICATION_ARGS);
        
        Display.getDefault();
        
        set.getLoadOptions().put(XMIResource.OPTION_RECORD_UNKNOWN_FEATURE, true);
        
        for (final String arg: appArgs) {
            if (!new File(arg).exists()) {
                continue;
            }
            
            this.renderDiagramOf(arg);
        }
        
        return IApplication.EXIT_OK;
    }
    
    private void renderDiagramOf(final String fileName) throws IOException {
        
        final String targetFile = fileName.replaceFirst("\\p{Punct}\\w*\\z", ".svg"); 
        new File(targetFile).delete();

        // load the source model
        final Resource res = set.getResource(URI.createFileURI(fileName), true);
        if (res.getContents().isEmpty()) {
            return;
        }
        
        final EObject eo = res.getContents().get(0);
        if (eo == null) {
            return;
        }
        
        // execute klighd and render using
        final IStatus result =
                LightDiagramServices.renderOffScreen(
                        eo,
                        "svg",
                        targetFile,
                        // select the specific generator
                        KlighdSynthesisProperties.create().setProperty2(
                                SVGOffscreenRenderer.GENERATOR,
                                GENERATOR_SVG_FREEHEP_SEMANTIC));

        if (result != null && result.getCode() == IStatus.OK) {
            System.out.println("Generated file " + targetFile);            
        } else {
            System.out.println("Generation of diagram to stored in " + targetFile + " failed.");
            if (result != null && result.getException() != null) {
                result.getException().printStackTrace();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void stop() {
    }
}

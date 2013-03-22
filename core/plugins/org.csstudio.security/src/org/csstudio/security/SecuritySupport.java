/*******************************************************************************
 * Copyright (c) 2013 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/
package org.csstudio.security;

import java.security.Principal;
import java.util.Set;
import java.util.logging.Logger;

import javax.security.auth.Subject;

import org.csstudio.security.authorization.AuthorizationProvider;
import org.csstudio.security.authorization.Authorizations;
import org.csstudio.security.internal.SecurityContext;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.Platform;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import com.sun.security.auth.NTUserPrincipal;
import com.sun.security.auth.UnixPrincipal;
import com.sun.security.auth.UserPrincipal;

/** Plugin activator,
 *  API entry point for obtaining authentication and authorization info
 *  
 *  @author Kay Kasemir
 */
@SuppressWarnings("nls")
public class SecuritySupport implements BundleActivator
{
    /** Plugin ID defined in MANIFEST.MF */
    public static String ID = "org.csstudio.security";
   
    /** Singleton {@link SecurityContext} */
    private static SecurityContext security = null;

    /** {@inheritDoc} */
    @Override
    public void start(final BundleContext context) throws Exception
    {
        // Fetch (i.e. create) the SecurityContext
        security = SecurityContext.getInstance();

        final Logger logger = Logger.getLogger(getClass().getName());
        
        // Obtain Authorization implementation from extension point
        final String authorization_name = SecurityPreferences.getAuthorizationProvider();
        final IConfigurationElement[] extensions =
            Platform.getExtensionRegistry().getConfigurationElementsFor(AuthorizationProvider.EXT_ID);
        for (IConfigurationElement extension : extensions)
        {
            final String name = extension.getAttribute("name");
            logger.finer("Found authentication provider " + name);
            if (name.equals(authorization_name))
            {
                logger.fine("Using authentication provider " + name +
                        " from " + extension.getContributor().getName());
                final AuthorizationProvider auth_provider =
                    (AuthorizationProvider) extension.createExecutableExtension("class");
                security.setAuthorizationProvider(auth_provider);
                break;
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void stop(final BundleContext context) throws Exception
    {
        security = null;
    }

    /** @param listener Listener to add */
    public static void addListener(final SecurityListener listener)
    {
        security.addListener(listener);
    }
    
    /** @param listener Listener to remove */
    public static void removeListener(final SecurityListener listener)
    {
        security.removeListener(listener);
    }

    /** @return Currently logged-in Subject or <code>null</code> */
    public static Subject getSubject()
    {
        return security.getSubject();
    }
    
    /** A Subject can have multiple Principals.
     * 
     *  <p>Attempt to determine the 'primary' Principal
     *  @param user Subject that describes user
     *  @return Primary user name
     *  @throws Exception if name cannot be determined
     */
    public static String getSubjectName(final Subject user) throws Exception
    {
        final Set<Principal> principals = user.getPrincipals();
        for (Principal principal : principals)
        {
            // If there's only one, use that
            if (principals.size() == 1)
                return principal.getName();
            
            // Try to identify the 'primary' one.
            // Not UnixNumericUserPrincipal, ..
            // but the one that has the actual name.
            if (principal instanceof UnixPrincipal  ||
                principal instanceof UserPrincipal  ||
                principal instanceof NTUserPrincipal)
                return principal.getName();
        }
        throw new Exception("Cannot determine name for " + user);
    }

    /** @return {@link Authorizations} of the currently logged-in Subject, or <code>null</code> */
    public static Authorizations getAuthorizations()
    {
        return security.getAuthorizations();
    }
    
    /** Check if user may do something
     * 
     *  @param authorization Authorization to check
     *  @return <code>true</code> if user has authorization
     */
    public static boolean havePermission(final String authorization)
    {
        return security.havePermission(authorization);
    }
}

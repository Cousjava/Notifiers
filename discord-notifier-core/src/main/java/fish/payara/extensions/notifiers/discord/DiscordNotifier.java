/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) [2020] Payara Foundation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://github.com/payara/Payara/blob/master/LICENSE.txt
 * See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * The Payara Foundation designates this particular file as subject to the "Classpath"
 * exception as provided by the Payara Foundation in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package fish.payara.extensions.notifiers.discord;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.UnknownHostException;
import java.text.MessageFormat;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.glassfish.api.StartupRunLevel;
import org.glassfish.hk2.runlevel.RunLevel;
import org.jvnet.hk2.annotations.Service;

import fish.payara.internal.notification.PayaraConfiguredNotifier;
import fish.payara.internal.notification.PayaraNotification;

/**
 * @author Matthew Gill
 */
@Service(name = "discord-notifier")
@RunLevel(StartupRunLevel.VAL)
public class DiscordNotifier extends PayaraConfiguredNotifier<DiscordNotifierConfiguration> {

    private static final Logger LOGGER = Logger.getLogger(DiscordNotifier.class.getName());

    private static final String DISCORD_ENDPOINT = "https://discordapp.com/api/webhooks/{0}/{1}";
    private static final String USER_AGENT = "Payara-Discord-Notifier";

    private URL url;
    private ObjectMapper mapper;

    @Override
    public void handleNotification(PayaraNotification event) {
        if (url == null) {
            LOGGER.fine("Discord notifier received notification, but no URL was available.");
            return;
        }

        try {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setRequestMethod(HttpMethod.POST);
            connection.setRequestProperty(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
            connection.addRequestProperty(HttpHeaders.USER_AGENT, USER_AGENT);
            connection.connect();

            try (OutputStream outputStream = connection.getOutputStream()) {
                if (LOGGER.isLoggable(Level.FINE)){
                    LOGGER.fine(mapper.writeValueAsString(event));
                }
                mapper.writeValue(outputStream, event);
            }

            // Discord API sometimes doesn't work without these
            connection.getInputStream().close();
            connection.disconnect();

            if (connection.getResponseCode() != 204) {
                LOGGER.log(Level.SEVERE, "Error occurred while connecting to Discord. "
                        + "Check your tokens. HTTP response code: " + connection.getResponseCode());
            } else {
                LOGGER.log(Level.FINE, "Message sent successfully");
            }
        } catch (MalformedURLException e) {
            LOGGER.log(Level.SEVERE, "Error occurred while accessing URL: " + url.toString(), e);
        } catch (ProtocolException e) {
            LOGGER.log(Level.SEVERE, "Specified URL is not accepting protocol defined: " + HttpMethod.POST, e);
        } catch (UnknownHostException e) {
            LOGGER.log(Level.SEVERE, "Check your network connection. Cannot access URL: " + url.toString(), e);
        } catch (ConnectException e) {
            LOGGER.log(Level.SEVERE, "Error occurred while connecting URL: " + url.toString(), e);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "IO Error while accessing URL: " + url.toString(), e);
        }
    }

    @Override
    public void bootstrap() {
        String formattedURL = MessageFormat.format(DISCORD_ENDPOINT,
                configuration.getWebhookId(),
                configuration.getWebhookToken());
        try {
            this.url = new URL(formattedURL);
        } catch (MalformedURLException e) {
            LOGGER.log(Level.SEVERE, "Error occurred while accessing URL: " + formattedURL, e);
        }

        this.mapper = new ObjectMapper();
        mapper.registerModule(DiscordSerializer.createModule());
    }

    @Override
    public void destroy() {
        this.url = null;
    }

}
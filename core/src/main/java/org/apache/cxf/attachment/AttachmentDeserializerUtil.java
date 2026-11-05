/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.cxf.attachment;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class AttachmentDeserializerUtil {

    private AttachmentDeserializerUtil() {
    }

    /**
     * Move the read pointer to the begining of the first part read till the end
     * of first boundary
     *
     * @param pushbackInStream
     * @param boundary
     * @throws MessagingException
     */
    static boolean readTillFirstBoundary(PushbackInputStream pbs, byte[] bp) throws IOException {

        // work around a bug in PushBackInputStream where the buffer isn't
        // initialized
        // and available always returns 0.
        int value = pbs.read();
        pbs.unread(value);
        while (value != -1) {
            value = pbs.read();
            if ((byte) value == bp[0]) {
                int boundaryIndex = 0;
                while (value != -1 && (boundaryIndex < bp.length) && ((byte) value == bp[boundaryIndex])) {

                    value = pbs.read();
                    if (value == -1) {
                        throw new IOException("Unexpected End while searching for first Mime Boundary");
                    }
                    boundaryIndex++;
                }
                if (boundaryIndex == bp.length) {
                    // boundary found, read the newline
                    if (value == 13) {
                        pbs.read();
                    }
                    return true;
                }
            }
        }
        return false;
    }

    static Map<String, List<String>> loadPartHeaders(InputStream in, int maxHeadersCount) throws IOException {
        Map<String, List<String>> heads = new TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER);
        StringBuffer buffer = new StringBuffer(128);
        String line;
        // loop until we hit the end or a null line
        while ((line = readLine(in)) != null) {
            // lines beginning with white space get special handling
            if (line.startsWith(" ") || line.startsWith("\t")) {
                // If this line is a continuation, but there's nothing before it,
                // there is no header to continue so the line is just dropped
                if (buffer.length() != 0) {
                    // preserve the line break and append the continuation
                    buffer.append("\r\n");
                    buffer.append(line);
                }
            } else {
                // if we have a line pending in the buffer, flush it
                if (buffer.length() > 0) {
                    addHeaderLine(heads, buffer.toString(), maxHeadersCount);
                    buffer.setLength(0);
                }
                // add this to the accumulator
                buffer.append(line);
            }
        }

        // if we have a line pending in the buffer, flush it
        if (buffer.length() > 0) {
            addHeaderLine(heads, buffer.toString(), maxHeadersCount);
        }
        return heads;
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuffer buffer = new StringBuffer(128);

        int c;

        while ((c = in.read()) != -1) {
            // a linefeed is a terminator, always.
            if (c == '\n') {
                break;
            } else if (c == '\r') {
                //just ignore the CR.  The next character SHOULD be an NL.  If not, we're
                //just going to discard this
                continue;
            } else {
                // just add to the buffer
                buffer.append((char)c);
            }
        }

        // no characters found...this was either an eof or a null line.
        if (buffer.length() == 0) {
            return null;
        }

        return buffer.toString();
    }

    private static void addHeaderLine(Map<String, List<String>> heads, String h,
            int maxHeadersCount) throws IOException {
        // null lines are a nop
        if (h.length() == 0) {
            return;
        }
        int separator = h.indexOf(':');
        String name = null;
        String value = "";
        if (separator == -1) {
            name = h.trim();
        } else {
            name = h.substring(0, separator);
            // step past the separator.  Now we need to remove any leading white space characters.
            separator++;

            while (separator < h.length()) {
                char ch = h.charAt(separator);
                if (ch != ' ' && ch != '\t' && ch != '\r' && ch != '\n') {
                    break;
                }
                separator++;
            }
            value = h.substring(separator);
        }

        if (heads.size() >= maxHeadersCount) {
            throw new IOException("The attachment contains more headers than are permitted");
        }
        List<String> v = heads.get(name);
        if (v == null) {
            v = new ArrayList<String>(1);
            heads.put(name, v);
        }
        v.add(value);
    }

}

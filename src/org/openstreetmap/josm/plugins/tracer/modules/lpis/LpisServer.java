/**
 *  Tracer - plugin for JOSM (RUIAN support)
 *  Jan Bilak, Marian Kyral
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along
 *  with this program; if not, write to the Free Software Foundation, Inc.,
 *  51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package org.openstreetmap.josm.plugins.tracer.modules.lpis;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.util.List;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.plugins.tracer.TracerUtils;
import org.xml.sax.SAXException;

public class LpisServer {

    private static String m_url;

    public LpisServer(String url) {
        m_url = url;
    }

    /**
     * Call Trace server.
     * @param urlString Input parameters.
     * @return Result text.
     */
    private String callServer(String urlString) throws MalformedURLException, UnsupportedEncodingException, IOException {
        try (BufferedReader reader = TracerUtils.openUrlStream (urlString, "UTF-8")) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() > 0)
                    sb.append(" ");
                sb.append(line);
            }
            return sb.toString();
        }
    }

    /**
     * Get element ID and geometry of the land on the position.
     * @param pos Position of the land.
     * @param url LPIS WFS service URL
     * @return Land ID and geometry.
     * @throws java.io.UnsupportedEncodingException
     * @throws javax.xml.parsers.ParserConfigurationException
     * @throws org.xml.sax.SAXException
     * @throws javax.xml.xpath.XPathExpressionException
     */
    public LpisRecord getRecord (LatLon pos, double adjlat, double adjlon) throws UnsupportedEncodingException, IOException, ParserConfigurationException, SAXException, XPathExpressionException {
        krovak k = new krovak();
        xyCoor xy = k.LatLon2krovak(pos);

        System.out.println ("LatLon: "+pos+" <-> XY: "+xy.x()+" "+xy.y());
        String bbox = xy.x()+","+xy.y()+","+xy.x()+","+xy.y();

        String request = m_url + "?VERSION=1.1.0&SERVICE=WFS&REQUEST=GetFeature&TYPENAME=LPIS_FB4_BBOX&bbox="+bbox+"&SRSNAME=EPSG:102067";

        System.out.println("Request: " + request);
        String content = callServer(request);
        System.out.println("Reply: " + content);
        LpisRecord lpis = new LpisRecord(adjlat, adjlon);
        lpis.parseXML("basic", content);

        // get additional information for given ID
        if (lpis.getLpisID() > 0) {
            request = m_url + "?VERSION=1.1.0&SERVICE=WFS&REQUEST=GetFeature&TYPENAME=LPIS_FB4&&featureID=LPIS_FB4."+lpis.getLpisID()+"&SRSNAME=EPSG:102067";
            System.out.println("Request: " + request);
            content = callServer(request);
            System.out.println("Reply: " + content);
            lpis.parseXML("extra", content);
        }
        return lpis;
    }

    public List<LpisRecord> getMultipleRecords (BBox bbox, double adjlat, double adjlon) throws UnsupportedEncodingException, IOException, ParserConfigurationException, SAXException, XPathExpressionException {
        krovak k = new krovak();

        LatLon a = bbox.getTopLeft();
        LatLon b = bbox.getBottomRight();

        xyCoor axy = k.LatLon2krovak(a);
        xyCoor bxy = k.LatLon2krovak(b);

        String wfsbox = axy.x()+","+axy.y()+","+bxy.x()+","+bxy.y();

        String request = m_url + "?VERSION=1.1.0&SERVICE=WFS&REQUEST=GetFeature&TYPENAME=LPIS_FB4_BBOX&bbox="+wfsbox+"&SRSNAME=EPSG:102067";

        System.out.println("Request: " + request);
        String content = callServer(request);
        System.out.println("Reply: " + content);

        List<LpisRecord> list = LpisRecord.parseBasicXML (content, adjlat, adjlon);

        for (LpisRecord lpis: list) {
            if (lpis.getLpisID() <= 0 || !lpis.hasOuter())
                continue;
            request = m_url + "?VERSION=1.1.0&SERVICE=WFS&REQUEST=GetFeature&TYPENAME=LPIS_FB4&&featureID=LPIS_FB4."+lpis.getLpisID()+"&SRSNAME=EPSG:102067";
            System.out.println("Request: " + request);
            content = callServer(request);
            System.out.println("Reply: " + content);
            lpis.parseXML("extra", content);
        }

        return list;
    }
}

/******************************************************************************
 * Copyright (C) 2008 Low Heng Sin                                            *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 *****************************************************************************/
package org.adempiere.webui.component;

/**
 * URL Box
 * @author Low Heng Sin
 */
public class Urlbox extends EditorBox
{
    /**
	 * 
	 */
	private static final long serialVersionUID = -3846071364733430994L;

	public Urlbox()
    {
        super();
    }

    /**
     * @param url
     */
    public Urlbox(String url)
    {
        super();
        setText(url);
    }
    
    @Override
	public void setEnabled(boolean enabled) {
    	txt.setReadonly(!enabled);
	}
}
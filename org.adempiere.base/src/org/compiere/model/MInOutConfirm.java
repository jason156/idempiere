/******************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                       *
 * Copyright (C) 1999-2006 ComPiere, Inc. All Rights Reserved.                *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * For the text or an alternative of this public license, you may reach us    *
 * ComPiere, Inc., 2620 Augustine Dr. #245, Santa Clara, CA 95054, USA        *
 * or via info@compiere.org or http://www.compiere.org/license.html           *
 *****************************************************************************/
package org.compiere.model;

import java.io.File;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.process.DocAction;
import org.compiere.process.DocumentEngine;
import org.compiere.util.CLogger;
import org.compiere.util.Env;
import org.compiere.util.Msg;

/**
 *	Shipment Confirmation Model
 *	
 *  @author Jorg Janke
 *  @version $Id: MInOutConfirm.java,v 1.3 2006/07/30 00:51:03 jjanke Exp $
 * 
 * @author Teo Sarca, www.arhipac.ro
 * 			<li>BF [ 2800460 ] System generate Material Receipt with no lines
 * 				https://sourceforge.net/tracker/?func=detail&atid=879332&aid=2800460&group_id=176962
 * @author Teo Sarca, teo.sarca@gmail.com
 * 			<li>BF [ 2993853 ] Voiding/Reversing Receipt should void confirmations
 * 				https://sourceforge.net/tracker/?func=detail&atid=879332&aid=2993853&group_id=176962
 * 			<li>FR [ 2994115 ] Add C_DocType.IsPrepareSplitDoc flag
 * 				https://sourceforge.net/tracker/?func=detail&aid=2994115&group_id=176962&atid=879335
 */
public class MInOutConfirm extends X_M_InOutConfirm implements DocAction
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 5270365186462536874L;

	/**
	 * 	Create Confirmation or return existing one
	 *	@param ship shipment
	 *	@param confirmType confirmation type
	 *	@param checkExisting if false, new confirmation is created
	 *	@return Confirmation
	 */
	public static MInOutConfirm create (MInOut ship, String confirmType, boolean checkExisting)
	{
		if (checkExisting)
		{
			MInOutConfirm[] confirmations = ship.getConfirmations(false);
			for (int i = 0; i < confirmations.length; i++)
			{
				MInOutConfirm confirm = confirmations[i];
				if (confirm.getConfirmType().equals(confirmType))
				{
					s_log.info("create - existing: " + confirm);
					return confirm;
				}
			}
		}

		MInOutConfirm confirm = new MInOutConfirm (ship, confirmType);
		confirm.saveEx();
		MInOutLine[] shipLines = ship.getLines(false);
		for (int i = 0; i < shipLines.length; i++)
		{
			MInOutLine sLine = shipLines[i];
			MInOutLineConfirm cLine = new MInOutLineConfirm (confirm);
			cLine.setInOutLine(sLine);
			cLine.saveEx();
		}
		s_log.info("New: " + confirm);
		return confirm;
	}	//	MInOutConfirm
	
	/**	Static Logger	*/
	private static CLogger	s_log	= CLogger.getCLogger (MInOutConfirm.class);
	
	/**************************************************************************
	 * 	Standard Constructor
	 *	@param ctx context
	 *	@param M_InOutConfirm_ID id
	 *	@param trxName transaction
	 */
	public MInOutConfirm (Properties ctx, int M_InOutConfirm_ID, String trxName)
	{
		super (ctx, M_InOutConfirm_ID, trxName);
		if (M_InOutConfirm_ID == 0)
		{
		//	setConfirmType (null);
			setDocAction (DOCACTION_Complete);	// CO
			setDocStatus (DOCSTATUS_Drafted);	// DR
			setIsApproved (false);
			setIsCancelled (false);
			setIsInDispute(false);
			super.setProcessed (false);
		}
	}	//	MInOutConfirm

	/**
	 * 	Load Constructor
	 *	@param ctx context
	 *	@param rs result set
	 *	@param trxName transaction
	 */
	public MInOutConfirm (Properties ctx, ResultSet rs, String trxName)
	{
		super(ctx, rs, trxName);
	}	//	MInOutConfirm

	/**
	 * 	Parent Constructor
	 *	@param ship shipment
	 *	@param confirmType confirmation type
	 */
	public MInOutConfirm (MInOut ship, String confirmType)
	{
		this (ship.getCtx(), 0, ship.get_TrxName());
		setClientOrg(ship);
		setM_InOut_ID (ship.getM_InOut_ID());
		setConfirmType (confirmType);
	}	//	MInOutConfirm
	
	/**	Confirm Lines					*/
	private MInOutLineConfirm[]	m_lines = null;
	/** Credit Memo to create			*/
	private MInvoice			m_creditMemo = null;
	/**	Physical Inventory to create	*/
	private MInventory			m_inventory = null;

	/**
	 * 	Get Lines
	 *	@param requery requery
	 *	@return array of lines
	 */
	public MInOutLineConfirm[] getLines (boolean requery)
	{
		if (m_lines != null && !requery) {
			set_TrxName(m_lines, get_TrxName());
			return m_lines;
		}
		final String whereClause = I_M_InOutLineConfirm.COLUMNNAME_M_InOutConfirm_ID+"=?";
		List<MInOutLineConfirm> list = new Query(getCtx(), I_M_InOutLineConfirm.Table_Name, whereClause, get_TrxName())
		.setParameters(getM_InOutConfirm_ID())
		.list();
		m_lines = new MInOutLineConfirm[list.size ()];
		list.toArray (m_lines);
		return m_lines;
	}	//	getLines
	
	/**
	 * 	Add to Description
	 *	@param description text
	 */
	public void addDescription (String description)
	{
		String desc = getDescription();
		if (desc == null)
			setDescription(description);
		else{
			StringBuilder msgd = new StringBuilder(desc).append(" | ").append(description);
			setDescription(msgd.toString());
		}	
	}	//	addDescription
	
	/**
	 * 	Get Name of ConfirmType
	 *	@return confirm type
	 */
	public String getConfirmTypeName ()
	{
		return MRefList.getListName (getCtx(), CONFIRMTYPE_AD_Reference_ID, getConfirmType());
	}	//	getConfirmTypeName
	
	/**
	 * 	String Representation
	 *	@return info
	 */
	public String toString ()
	{
		StringBuilder sb = new StringBuilder ("MInOutConfirm[");
		sb.append(get_ID()).append("-").append(getSummary())
			.append ("]");
		return sb.toString ();
	}	//	toString
	
	/**
	 * 	Get Document Info
	 *	@return document info (untranslated)
	 */
	public String getDocumentInfo()
	{
		StringBuilder msgreturn = new StringBuilder().append(Msg.getElement(getCtx(), "M_InOutConfirm_ID")).append(" ").append(getDocumentNo());
		return msgreturn.toString();
	}	//	getDocumentInfo

	/**
	 * 	Create PDF
	 *	@return File or null
	 */
	public File createPDF ()
	{
		try
		{
			StringBuilder msgfile = new StringBuilder().append(get_TableName()).append(get_ID()).append("_");
			File temp = File.createTempFile(msgfile.toString(), ".pdf");
			return createPDF (temp);
		}
		catch (Exception e)
		{
			log.severe("Could not create PDF - " + e.getMessage());
		}
		return null;
	}	//	getPDF

	/**
	 * 	Create PDF file
	 *	@param file output file
	 *	@return file if success
	 */
	public File createPDF (File file)
	{
	//	ReportEngine re = ReportEngine.get (getCtx(), ReportEngine.INVOICE, getC_Invoice_ID());
	//	if (re == null)
			return null;
	//	return re.getPDF(file);
	}	//	createPDF

	/**
	 * 	Set Approved
	 *	@param IsApproved approval
	 */
	public void setIsApproved (boolean IsApproved)
	{
		if (IsApproved && !isApproved())
		{
			int AD_User_ID = Env.getAD_User_ID(getCtx());
			MUser user = MUser.get(getCtx(), AD_User_ID);
			StringBuilder info = new StringBuilder().append(user.getName()) 
				.append(": ")
				.append(Msg.translate(getCtx(), "IsApproved"))
				.append(" - ").append(new Timestamp(System.currentTimeMillis()));
			addDescription(info.toString());
		}
		super.setIsApproved (IsApproved);
	}	//	setIsApproved
	
	
	/**************************************************************************
	 * 	Process document
	 *	@param processAction document action
	 *	@return true if performed
	 */
	public boolean processIt (String processAction)
	{
		m_processMsg = null;
		DocumentEngine engine = new DocumentEngine (this, getDocStatus());
		return engine.processIt (processAction, getDocAction());
	}	//	processIt
	
	/**	Process Message 			*/
	private String		m_processMsg = null;
	/**	Just Prepared Flag			*/
	private boolean		m_justPrepared = false;

	/**
	 * 	Unlock Document.
	 * 	@return true if success 
	 */
	public boolean unlockIt()
	{
		log.info(toString());
		setProcessing(false);
		return true;
	}	//	unlockIt
	
	/**
	 * 	Invalidate Document
	 * 	@return true if success 
	 */
	public boolean invalidateIt()
	{
		log.info(toString());
		setDocAction(DOCACTION_Prepare);
		return true;
	}	//	invalidateIt
	
	/**
	 *	Prepare Document
	 * 	@return new status (In Progress or Invalid) 
	 */
	public String prepareIt()
	{
		log.info(toString());
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_BEFORE_PREPARE);
		if (m_processMsg != null)
			return DocAction.STATUS_Invalid;

		/**
		MDocType dt = MDocType.get(getCtx(), getC_DocTypeTarget_ID());

		//	Std Period open?
		if (!MPeriod.isOpen(getCtx(), getDateAcct(), dt.getDocBaseType()))
		{
			m_processMsg = "@PeriodClosed@";
			return DocAction.STATUS_Invalid;
		}
		**/
		
		MInOutLineConfirm[] lines = getLines(true);
		if (lines.length == 0)
		{
			m_processMsg = "@NoLines@";
			return DocAction.STATUS_Invalid;
		}
		//	Set dispute if not fully confirmed
		boolean difference = false;
		for (int i = 0; i < lines.length; i++)
		{
			if (!lines[i].isFullyConfirmed())
			{
				difference = true;
				break;
			}
		}
		setIsInDispute(difference);

		m_processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_AFTER_PREPARE);
		if (m_processMsg != null)
			return DocAction.STATUS_Invalid;
		//
		m_justPrepared = true;
		if (!DOCACTION_Complete.equals(getDocAction()))
			setDocAction(DOCACTION_Complete);
		return DocAction.STATUS_InProgress;
	}	//	prepareIt
	
	/**
	 * 	Approve Document
	 * 	@return true if success 
	 */
	public boolean  approveIt()
	{
		log.info(toString());
		setIsApproved(true);
		return true;
	}	//	approveIt
	
	/**
	 * 	Reject Approval
	 * 	@return true if success 
	 */
	public boolean rejectIt()
	{
		log.info(toString());
		setIsApproved(false);
		return true;
	}	//	rejectIt
	
	/**
	 * 	Complete Document
	 * 	@return new status (Complete, In Progress, Invalid, Waiting ..)
	 */
	public String completeIt()
	{
		//	Re-Check
		if (!m_justPrepared)
		{
			String status = prepareIt();
			if (!DocAction.STATUS_InProgress.equals(status))
				return status;
		}
		
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_BEFORE_COMPLETE);
		if (m_processMsg != null)
			return DocAction.STATUS_Invalid;
		
		//	Implicit Approval
		if (!isApproved())
			approveIt();
		log.info(toString());
		//
		MInOut inout = new MInOut (getCtx(), getM_InOut_ID(), get_TrxName());
		MInOutLineConfirm[] lines = getLines(false);
		
		//	Check if we need to split Shipment
		if (isInDispute())
		{
			MDocType dt = MDocType.get(getCtx(), inout.getC_DocType_ID());
			if (dt.isSplitWhenDifference())
			{
				if (dt.getC_DocTypeDifference_ID() == 0)
				{
					m_processMsg = "No Split Document Type defined for: " + dt.getName();
					return DocAction.STATUS_Invalid;
				}
				splitInOut (inout, dt.getC_DocTypeDifference_ID(), lines);
				m_lines = null;
			}
		}
		
		//	All lines
		for (int i = 0; i < lines.length; i++)
		{
			MInOutLineConfirm confirmLine = lines[i];
			confirmLine.set_TrxName(get_TrxName());
			if (!confirmLine.processLine (inout.isSOTrx(), getConfirmType()))
			{
				m_processMsg = "ShipLine not saved - " + confirmLine;
				return DocAction.STATUS_Invalid;
			}
			if (confirmLine.isFullyConfirmed())
			{
				confirmLine.setProcessed(true);
				confirmLine.saveEx();
			}
			else
			{
				if (createDifferenceDoc (inout, confirmLine))
				{
					confirmLine.setProcessed(true);
					confirmLine.saveEx();
				}
				else
				{
					log.log(Level.SEVERE, "Scrapped=" + confirmLine.getScrappedQty()
						+ " - Difference=" + confirmLine.getDifferenceQty());
					return DocAction.STATUS_Invalid;
				}
			}
		}	//	for all lines

		if (m_creditMemo != null)
			m_processMsg += " @C_Invoice_ID@=" + m_creditMemo.getDocumentNo();
		if (m_inventory != null)
			m_processMsg += " @M_Inventory_ID@=" + m_inventory.getDocumentNo();

		
		//	Try to complete Shipment
	//	if (inout.processIt(DocAction.ACTION_Complete))
	//		m_processMsg = "@M_InOut_ID@ " + inout.getDocumentNo() + ": @Completed@";
		
		//	User Validation
		String valid = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_AFTER_COMPLETE);
		if (valid != null)
		{
			m_processMsg = valid;
			return DocAction.STATUS_Invalid;
		}

		setProcessed(true);
		setDocAction(DOCACTION_Close);
		return DocAction.STATUS_Completed;
	}	//	completeIt

	/**
	 * 	Split Shipment into confirmed and dispute
	 *	@param original original shipment
	 *	@param C_DocType_ID target DocType
	 *	@param confirmLines confirm lines
	 */
	private void splitInOut (MInOut original, int C_DocType_ID, MInOutLineConfirm[] confirmLines)
	{
		MInOut split = null;
		//	Go through confirmations 
		for (int i = 0; i < confirmLines.length; i++)
		{
			MInOutLineConfirm confirmLine = confirmLines[i];
			BigDecimal differenceQty = confirmLine.getDifferenceQty();
			if (differenceQty.compareTo(Env.ZERO) == 0)
				continue;
			//
			MInOutLine oldLine = confirmLine.getLine();
			log.fine("Qty=" + differenceQty + ", Old=" + oldLine);
			//
			// Create Header
			if (split == null)
			{
				split = new MInOut (original, C_DocType_ID, original.getMovementDate());
				StringBuilder msgd = new StringBuilder("Splitted from ").append(original.getDocumentNo());
				split.addDescription(msgd.toString());
				split.setIsInDispute(true);
				split.saveEx();
				msgd = new StringBuilder("Split: ").append(split.getDocumentNo());
				original.addDescription(msgd.toString());
				original.saveEx();
			}
			//
			MInOutLine splitLine = new MInOutLine (split);
			splitLine.setC_OrderLine_ID(oldLine.getC_OrderLine_ID());
			splitLine.setC_UOM_ID(oldLine.getC_UOM_ID());
			splitLine.setDescription(oldLine.getDescription());
			splitLine.setIsDescription(oldLine.isDescription());
			splitLine.setLine(oldLine.getLine());
			splitLine.setM_AttributeSetInstance_ID(oldLine.getM_AttributeSetInstance_ID());
			splitLine.setM_Locator_ID(oldLine.getM_Locator_ID());
			splitLine.setM_Product_ID(oldLine.getM_Product_ID());
			splitLine.setM_Warehouse_ID(oldLine.getM_Warehouse_ID());
			splitLine.setRef_InOutLine_ID(oldLine.getRef_InOutLine_ID());
			StringBuilder msgd = new StringBuilder("Split: from ").append(oldLine.getMovementQty());
			splitLine.addDescription(msgd.toString());
			//	Qtys
			splitLine.setQty(differenceQty);		//	Entered/Movement
			splitLine.saveEx();
			//	Old
			msgd = new StringBuilder("Splitted: from ").append(oldLine.getMovementQty());
			oldLine.addDescription(msgd.toString());
			oldLine.setQty(oldLine.getMovementQty().subtract(differenceQty));
			oldLine.saveEx();
			//	Update Confirmation Line
			confirmLine.setTargetQty(confirmLine.getTargetQty().subtract(differenceQty));
			confirmLine.setDifferenceQty(Env.ZERO);
			confirmLine.saveEx();
		}	//	for all confirmations
		
		// Nothing to split
		if (split == null)
		{
			return ;
		}

		m_processMsg = "Split @M_InOut_ID@=" + split.getDocumentNo()
			+ " - @M_InOutConfirm_ID@=";
		
		MDocType dt = MDocType.get(getCtx(), original.getC_DocType_ID());
		if (!dt.isPrepareSplitDocument())
		{
			return ;
		}
		
		//	Create Dispute Confirmation
		if (!split.processIt(DocAction.ACTION_Prepare))
			throw new AdempiereException(split.getProcessMsg());
	//	split.createConfirmation();
		split.saveEx();
		MInOutConfirm[] splitConfirms = split.getConfirmations(true);
		if (splitConfirms.length > 0)
		{
			int index = 0;
			if (splitConfirms[index].isProcessed())
			{
				if (splitConfirms.length > 1)
					index++;	//	try just next
				if (splitConfirms[index].isProcessed())
				{
					m_processMsg += splitConfirms[index].getDocumentNo() + " processed??";
					return;
				}
			}
			splitConfirms[index].setIsInDispute(true);
			splitConfirms[index].saveEx();
			m_processMsg += splitConfirms[index].getDocumentNo();
			//	Set Lines to unconfirmed
			MInOutLineConfirm[] splitConfirmLines = splitConfirms[index].getLines(false);
			for (int i = 0; i < splitConfirmLines.length; i++)
			{
				MInOutLineConfirm splitConfirmLine = splitConfirmLines[i];
				splitConfirmLine.setScrappedQty(Env.ZERO);
				splitConfirmLine.setConfirmedQty(Env.ZERO);
				splitConfirmLine.saveEx();
			}
		}
		else
			m_processMsg += "??";
		
	}	//	splitInOut

	
	/**
	 * 	Create Difference Document
	 * 	@param inout shipment/receipt
	 *	@param confirm confirm line
	 *	@return true if created
	 */
	private boolean createDifferenceDoc (MInOut inout, MInOutLineConfirm confirm)
	{
		if (m_processMsg == null)
			m_processMsg = "";
		else if (m_processMsg.length() > 0)
			m_processMsg += "; ";
		//	Credit Memo if linked Document
		if (confirm.getDifferenceQty().signum() != 0
			&& !inout.isSOTrx() && inout.getRef_InOut_ID() != 0)
		{
			log.info("Difference=" + confirm.getDifferenceQty());
			if (m_creditMemo == null)
			{
				m_creditMemo = new MInvoice (inout, null);
				StringBuilder msgd = new StringBuilder().append(Msg.translate(getCtx(), "M_InOutConfirm_ID")).append(" ").append(getDocumentNo());
				m_creditMemo.setDescription(msgd.toString());
				m_creditMemo.setC_DocTypeTarget_ID(MDocType.DOCBASETYPE_APCreditMemo);
				m_creditMemo.saveEx();
				setC_Invoice_ID(m_creditMemo.getC_Invoice_ID());
			}
			MInvoiceLine line = new MInvoiceLine (m_creditMemo);
			line.setShipLine(confirm.getLine());
			if (confirm.getLine().getProduct() != null) {
				// use product UOM in case the shipment hasn't the same uom than the order
				line.setC_UOM_ID(confirm.getLine().getProduct().getC_UOM_ID());
			}
			// Note: confirmation is always in the qty according to the product UOM
			line.setQty(confirm.getDifferenceQty());	//	Entered/Invoiced
			line.saveEx();
			confirm.setC_InvoiceLine_ID(line.getC_InvoiceLine_ID());
		}
		
		//	Create Inventory Difference
		if (confirm.getScrappedQty().signum() != 0)
		{
			log.info("Scrapped=" + confirm.getScrappedQty());
			if (m_inventory == null)
			{
				MWarehouse wh = MWarehouse.get(getCtx(), inout.getM_Warehouse_ID());
				m_inventory = new MInventory (wh, get_TrxName());
				StringBuilder msgd = new StringBuilder().append(Msg.translate(getCtx(), "M_InOutConfirm_ID")).append(" ").append(getDocumentNo());
				m_inventory.setDescription(msgd.toString());
				m_inventory.saveEx();
				setM_Inventory_ID(m_inventory.getM_Inventory_ID());
			}
			MInOutLine ioLine = confirm.getLine();
			MInventoryLine line = new MInventoryLine (m_inventory, 
				ioLine.getM_Locator_ID(), ioLine.getM_Product_ID(), ioLine.getM_AttributeSetInstance_ID(),
				confirm.getScrappedQty(), Env.ZERO);
			if (!line.save(get_TrxName()))
			{
				m_processMsg += "Inventory Line not created";
				return false;
			}
			confirm.setM_InventoryLine_ID(line.getM_InventoryLine_ID());
		}
		
		//
		if (!confirm.save(get_TrxName()))
		{
			m_processMsg += "Confirmation Line not saved";
			return false;
		}
		return true;
	}	//	createDifferenceDoc

	/**
	 * 	Void Document.
	 * 	@return false 
	 */
	public boolean voidIt()
	{
		log.info(toString());
		// Before Void
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,ModelValidator.TIMING_BEFORE_VOID);
		if (m_processMsg != null)
			return false;

		if (DOCSTATUS_Closed.equals(getDocStatus())
				|| DOCSTATUS_Reversed.equals(getDocStatus())
				|| DOCSTATUS_Voided.equals(getDocStatus()))
		{
			m_processMsg = "Document Closed: " + getDocStatus();
			return false;
		}

		//	Not Processed
		if (DOCSTATUS_Drafted.equals(getDocStatus())
				|| DOCSTATUS_Invalid.equals(getDocStatus())
				|| DOCSTATUS_InProgress.equals(getDocStatus())
				|| DOCSTATUS_Approved.equals(getDocStatus())
				|| DOCSTATUS_NotApproved.equals(getDocStatus()) )
		{
			MInOut inout = (MInOut)getM_InOut();
			if (!MInOut.DOCSTATUS_Voided.equals(inout.getDocStatus())
					&& !MInOut.DOCSTATUS_Reversed.equals(inout.getDocStatus()) )
			{
				throw new AdempiereException("@M_InOut_ID@ @DocStatus@<>VO");
			}
			for (MInOutLineConfirm confirmLine : getLines(true))
			{
				confirmLine.setTargetQty(Env.ZERO);
				confirmLine.setConfirmedQty(Env.ZERO);
				confirmLine.setScrappedQty(Env.ZERO);
				confirmLine.setDifferenceQty(Env.ZERO);
				confirmLine.setProcessed(true);
				confirmLine.saveEx();
			}
			setIsCancelled(true);
		}
		else
		{
			return reverseCorrectIt();
		}

		// After Void
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,ModelValidator.TIMING_AFTER_VOID);
		if (m_processMsg != null)
			return false;
		
		setProcessed(true);
		setDocAction(DOCACTION_None);
		
		return true;
	}	//	voidIt
	
	/**
	 * 	Close Document.
	 * 	@return true if success 
	 */
	public boolean closeIt()
	{
		log.info(toString());
		// Before Close
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,ModelValidator.TIMING_BEFORE_CLOSE);
		if (m_processMsg != null)
			return false;
		
		setDocAction(DOCACTION_None);

		// After Close
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,ModelValidator.TIMING_AFTER_CLOSE);
		if (m_processMsg != null)
			return false;

		return true;
	}	//	closeIt
	
	/**
	 * 	Reverse Correction
	 * 	@return false 
	 */
	public boolean reverseCorrectIt()
	{
		log.info(toString());
		// Before reverseCorrect
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,ModelValidator.TIMING_BEFORE_REVERSECORRECT);
		if (m_processMsg != null)
			return false;
		
		// After reverseCorrect
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,ModelValidator.TIMING_AFTER_REVERSECORRECT);
		if (m_processMsg != null)
			return false;
		
		return false;
	}	//	reverseCorrectionIt
	
	/**
	 * 	Reverse Accrual - none
	 * 	@return false 
	 */
	public boolean reverseAccrualIt()
	{
		log.info(toString());
		// Before reverseAccrual
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,ModelValidator.TIMING_BEFORE_REVERSEACCRUAL);
		if (m_processMsg != null)
			return false;
		
		// After reverseAccrual
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,ModelValidator.TIMING_AFTER_REVERSEACCRUAL);
		if (m_processMsg != null)
			return false;
		
		return false;
	}	//	reverseAccrualIt
	
	/** 
	 * 	Re-activate
	 * 	@return false 
	 */
	public boolean reActivateIt()
	{
		log.info(toString());
		// Before reActivate
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,ModelValidator.TIMING_BEFORE_REACTIVATE);
		if (m_processMsg != null)
			return false;	
		
		// After reActivate
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,ModelValidator.TIMING_AFTER_REACTIVATE);
		if (m_processMsg != null)
			return false;
		
		return false;
	}	//	reActivateIt
	
	
	/*************************************************************************
	 * 	Get Summary
	 *	@return Summary of Document
	 */
	public String getSummary()
	{
		StringBuilder sb = new StringBuilder();
		sb.append(getDocumentNo());
		//	: Total Lines = 123.00 (#1)
		sb.append(": ")
			.append(Msg.translate(getCtx(),"ApprovalAmt")).append("=").append(getApprovalAmt())
			.append(" (#").append(getLines(false).length).append(")");
		//	 - Description
		if (getDescription() != null && getDescription().length() > 0)
			sb.append(" - ").append(getDescription());
		return sb.toString();
	}	//	getSummary
	
	/**
	 * 	Get Process Message
	 *	@return clear text error message
	 */
	public String getProcessMsg()
	{
		return m_processMsg;
	}	//	getProcessMsg
	
	/**
	 * 	Get Document Owner (Responsible)
	 *	@return AD_User_ID
	 */
	public int getDoc_User_ID()
	{
		return getUpdatedBy();
	}	//	getDoc_User_ID

	/**
	 * 	Get Document Currency
	 *	@return C_Currency_ID
	 */
	public int getC_Currency_ID()
	{
	//	MPriceList pl = MPriceList.get(getCtx(), getM_PriceList_ID());
	//	return pl.getC_Currency_ID();
		return 0;
	}	//	getC_Currency_ID
	
}	//	MInOutConfirm
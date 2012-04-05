package com.flobi.floAuction;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.flobi.utility.functions;

public class Auction {
	protected floAuction plugin;
	private String[] args;
	private Player owner;

	private int startingBid = 0;
	private int minBidIncrement = 0;
	private int quantity = 0;
	private int time = 0;
	private boolean active = false;
	
	private AuctionLot lot;
	private AuctionBid currentBid;
	
	public Auction(floAuction plugin, Player auctionOwner, String[] inputArgs) {
		owner = auctionOwner;
		args = inputArgs;
		this.plugin = plugin; 

		// Remove the optional "start" arg:
		if (args.length > 0) {
			if (args[0].equalsIgnoreCase("start")) {
				args = new String[0];
				System.arraycopy(inputArgs, 1, args, 0, inputArgs.length - 1);
			}
		}
		
	}
	public Boolean start() {
		if (!lot.AddItems(quantity, true)) {
			plugin.sendMessage("auction-fail-insufficient-supply", owner, this);
			return false;
		}
		active = true;
		plugin.sendMessage("auction-start", null, this);
		info(null);
		return true;
	}
	public void info(CommandSender sender) {
		if (!active) {
			plugin.sendMessage("auction-info-no-auction", sender, this);
		} else if (currentBid == null) {
			plugin.sendMessage("auction-info-header-nobids", sender, this);
			plugin.sendMessage("auction-info-enchantment", sender, this);
			plugin.sendMessage("auction-info-footer-nobids", sender, this);
		} else {
			plugin.sendMessage("auction-info-header", sender, this);
			plugin.sendMessage("auction-info-enchantment", sender, this);
			plugin.sendMessage("auction-info-footer", sender, this);
		}
	}
	public void cancel(Player canceller) {
		plugin.sendMessage("auction-cancel", canceller, this);
		if (lot != null) lot.cancelLot();
		if (currentBid != null) currentBid.cancelBid();
	}
	public void end(Player ender) {
		// TODO: figure out how to clear auction object

		if (currentBid == null || lot == null) {
			plugin.sendMessage("auction-end-nobids", ender, this);
			if (lot != null) lot.cancelLot();
			if (currentBid != null) currentBid.cancelBid();
			return;
		}
		plugin.sendMessage("auction-end", ender, this);
		lot.winLot(currentBid.getBidder());
		currentBid.winBid();
	}
	public Boolean isValid() {
		if (!parseHeldItem()) return false;
		if (!parseArgs()) return false;
		if (!isValidOwner()) return false;
		if (!isValidAmount()) return false;
		if (!isValidStartingBid()) return false;
		if (!isValidIncrement()) return false;
		if (!isValidTime()) return false;
		return true;
	}
	public void Bid(Player bidder, String[] inputArgs) {
		AuctionBid bid = new AuctionBid(this, bidder, inputArgs);
		if (bid.getError() != null) {
			failBid(bid, bid.getError());
			return;
		}
		if (owner.equals(bidder)) {
			failBid(bid, "bid-fail-is-auction-owner");
			return;
		}
		if (currentBid == null) {
			setNewBid(bid, "bid-success-no-challenger");
			return;
		}
		if (currentBid.getBidder().equals(bidder)) {
			if (bid.outbid(currentBid)) {
				// TODO: There is also the message, BID_SUCCESS_UPDATE_OWN_MAX_BID, for increasing that w/o increasing bid. 
				setNewBid(bid, "bid-success-update-own-bid");
			} else {
				failBid(bid, "bid-fail-already-current-bidder");
			}
			return;
		}
		if (currentBid.outbid(bid)) {
			failBid(bid, "bid-fail-auto-outbid");
			return;
		}
		if (bid.outbid(currentBid)) {
			setNewBid(bid, "bid-success-outbid");
			return;
		}
		failBid(bid, "bid-fail-outbid-uncertainty");
	}
	private void failBid(AuctionBid newBid, String reason) {
		newBid.cancelBid();
		plugin.sendMessage(reason, newBid.getBidder(), this);
	}
	private void setNewBid(AuctionBid newBid, String reason) {
		currentBid.cancelBid();
		currentBid = newBid;
		plugin.sendMessage(reason, newBid.getBidder(), this);
	}
	private Boolean parseHeldItem() {
		ItemStack heldItem = owner.getItemInHand();
		if (heldItem == null || heldItem.getAmount() == 0) {
			plugin.sendMessage("auction-fail-hand-is-empty", owner, this);
			return false;
		}
		lot = new AuctionLot(heldItem, owner);
		return true;
	}
	private Boolean parseArgs() {
		// (amount) (starting price) (increment) (time)
		if (!parseArgAmount()) return false;
		if (!parseArgStartingBid()) return false;
		if (!parseArgIncrement()) return false;
		if (!parseArgTime()) return false;
		return true;
	}
	private Boolean isValidOwner() {
		if (owner == null) {
			plugin.sendMessage("auction-fail-invalid-owner", (Player) plugin.getServer().getConsoleSender(), this);
			return false;
		}
		return true;
	}
	private Boolean isValidAmount() {
		if (quantity <= 0) {
			plugin.sendMessage("auction-fail-quantity-too-low", owner, this);
			return false;
		}
		if (!functions.hasAmount(owner, quantity, lot.getTypeStack())) {
			plugin.sendMessage("auction-fail-insufficient-supply", owner, this);
			return false;
		}
		return true;
	}
	private Boolean isValidStartingBid() {
		if (startingBid < 0) {
			plugin.sendMessage("auction-fail-starting-bid-too-low", owner, this);
			return false;
		} else if (startingBid > plugin.maxStartingBid) {
			plugin.sendMessage("auction-fail-starting-bid-too-high", owner, this);
			return false;
		}
		return true;
	}
	private Boolean isValidIncrement() {
		if (getMinBidIncrement() < plugin.minIncrement) {
			plugin.sendMessage("auction-fail-increment-too-low", owner, this);
			return false;
		}
		if (getMinBidIncrement() > plugin.maxIncrement) {
			plugin.sendMessage("auction-fail-increment-too-high", owner, this);
			return false;
		}
		return true;
	}
	private Boolean isValidTime() {
		if (time < plugin.minTime) {
			plugin.sendMessage("auction-fail-time-too-low", owner, this);
			return false;
		}
		if (time > plugin.maxTime) {
			plugin.sendMessage("auction-fail-time-too-high", owner, this);
			return false;
		}
		return true;
	}
	private Boolean parseArgAmount() {
		ItemStack lotType = lot.getTypeStack();
		if (args.length > 0) {
			if (args[0].equalsIgnoreCase("this")) {
				quantity = lotType.getAmount();
			} else if (args[0].equalsIgnoreCase("all")) {
				quantity = functions.getAmount(owner, lotType);
			} else if (args[0].matches("[0-9]+")) {
				quantity = Integer.parseInt(args[0]);
			} else {
				plugin.getServer().broadcastMessage(args[0]);
				plugin.sendMessage("parse-error-invalid-quantity", owner, this);
				return false;
			}
		} else {
			quantity = lotType.getAmount();
		}
		return true;
	}
	private Boolean parseArgStartingBid() {
		if (args.length > 1) {
			if (args[1].matches("([0-9]*(\\.[0-9][0-9]?)?)")) {
				startingBid = functions.safeMoney(Double.parseDouble(args[1]));
			} else {
				plugin.sendMessage("parse-error-invalid-starting-bid", owner, this);
				return false;
			}
		} else {
			startingBid = plugin.defaultStartingBid;
		}
		return true;
	}
	private Boolean parseArgIncrement() {
		if (args.length > 2) {
			if (args[2].matches("([0-9]*(\\.[0-9][0-9]?)?)")) {
				minBidIncrement = functions.safeMoney(Double.parseDouble(args[2]));
			} else {
				plugin.sendMessage("parse-error-invalid-bid-increment", owner, this);
				return false;
			}
		} else {
			minBidIncrement = plugin.defaultBidIncrement;
		}
		return true;
	}
	private Boolean parseArgTime() {
		if (args.length > 3) {
			if (args[3].matches("[0-9]+")) {
				time = Integer.parseInt(args[3]);
			} else {
				plugin.sendMessage("parse-error-invalid-time", owner, this);
				return false;
			}
		} else {
			time = plugin.defaultAuctionTime;
		}
		return true;
	}
	public int getMinBidIncrement() {
		return minBidIncrement;
	}
	
	public ItemStack getLotType() {
		if (lot == null) {
			return null;
		}
		return lot.getTypeStack();
	}
	
	public int getLotQuantity() {
		if (lot == null) {
			return 0;
		}
		return lot.getQuantity();
	}
	public int getStartingBid() {
		return startingBid;
	}
	public AuctionBid getCurrentBid() {
		return currentBid;
	}
	public Player getOwner() {
		return owner;
	}
}
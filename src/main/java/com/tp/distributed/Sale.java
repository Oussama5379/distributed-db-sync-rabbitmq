package com.tp.distributed;

public class Sale {
	private int id;
	private String sale_date;
	private String region;
	private String product;
	private int qty;
	private double cost;
	private double amt;
	private double tax;
	private double total;
	private boolean is_synced;

	public Sale() {
	}

	public Sale(int id, String sale_date, String region, String product, int qty, double cost, double amt, double tax,
			double total, boolean is_synced) {
		this.id = id;
		this.sale_date = sale_date;
		this.region = region;
		this.product = product;
		this.qty = qty;
		this.cost = cost;
		this.amt = amt;
		this.tax = tax;
		this.total = total;
		this.is_synced = is_synced;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getSale_date() {
		return sale_date;
	}

	public void setSale_date(String sale_date) {
		this.sale_date = sale_date;
	}

	public String getRegion() {
		return region;
	}

	public void setRegion(String region) {
		this.region = region;
	}

	public String getProduct() {
		return product;
	}

	public void setProduct(String product) {
		this.product = product;
	}

	public int getQty() {
		return qty;
	}

	public void setQty(int qty) {
		this.qty = qty;
	}

	public double getCost() {
		return cost;
	}

	public void setCost(double cost) {
		this.cost = cost;
	}

	public double getAmt() {
		return amt;
	}

	public void setAmt(double amt) {
		this.amt = amt;
	}

	public double getTax() {
		return tax;
	}

	public void setTax(double tax) {
		this.tax = tax;
	}

	public double getTotal() {
		return total;
	}

	public void setTotal(double total) {
		this.total = total;
	}

	public boolean getIs_synced() {
		return is_synced;
	}

	public void setIs_synced(boolean is_synced) {
		this.is_synced = is_synced;
	}
}

package com.example.android_final_proj;


/**
 * A data structure to hold information about a discovered peer
 */
public class User
{
	
	String uniqueID;
	String IPaddr;
	String name;

	public User(String uniqueID,String IPaddr,String name)
	{
		this.uniqueID = uniqueID;
		this.IPaddr = IPaddr;
		this.name = name;
	}
	
	@Override
	public boolean equals(Object other_)
	{
		if(other_==null || !( other_ instanceof User) ){return false;}
		User other = (User)other_;
		
		if(uniqueID==null && IPaddr==null && name==null && other.uniqueID==null && other.IPaddr==null && other.name==null){return true;}
		
		if(other.uniqueID==null && uniqueID!=null){return false;}
		if(uniqueID==null && other.uniqueID!=null){return false;}
		
		if(other.IPaddr==null && IPaddr!=null){return false;}
		if(IPaddr==null && other.IPaddr!=null){return false;}
		
		if(other.name==null && name!=null){return false;}
		if(name==null && other.name!=null){return false;}
		
		
		if (other.uniqueID!=null && !other.uniqueID.equalsIgnoreCase(this.uniqueID))
			return false;
		if (other.IPaddr!=null && !other.IPaddr.equalsIgnoreCase(this.IPaddr))
			return false;
		if (other.name!=null && !other.name.equalsIgnoreCase(this.name))
			return false;
		
		return true;
	}
}//class

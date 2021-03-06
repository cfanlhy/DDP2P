/* ------------------------------------------------------------------------- */
/*   Copyright (C) 2013 Marius C. Silaghi
		Author: Marius Silaghi: msilaghi@fit.edu
		Florida Tech, Human Decision Support Systems Laboratory
   
       This program is free software; you can redistribute it and/or modify
       it under the terms of the GNU Affero General Public License as published by
       the Free Software Foundation; either the current version of the License, or
       (at your option) any later version.
   
      This program is distributed in the hope that it will be useful,
      but WITHOUT ANY WARRANTY; without even the implied warranty of
      MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
      GNU General Public License for more details.
  
      You should have received a copy of the GNU Affero General Public License
      along with this program; if not, write to the Free Software
      Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.              */
/* ------------------------------------------------------------------------- */

package hds;

import static java.lang.System.out;
import static util.Util._;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Hashtable;

import ciphersuits.Cipher;
import ciphersuits.SK;

import plugin_data.PluginRequest;

import streaming.RequestData;
import streaming.SpecificRequest;
import table.organization;
import table.peer;
import util.P2PDDSQLException;
import util.Util;
import widgets.directories.DirectoriesData;
import config.Application;
import config.DD;
import config.Identity;
import data.D_OrgDistribution;
import data.D_PeerAddress;
import data.D_PluginData;
import data.D_PluginInfo;

public class ClientSync{
	public static long PAUSE = 40000;	

	public static boolean DEBUG = false;
	static final boolean _DEBUG = true;
	private static final String CLASS = "ClassSync";
	/**
	 * Not using payload.requested when packing message GIDs since the GIDs would spell trouble.
	 * see message GID compression packing on the wire!!
	 * 
	 * No limit on size implemented
	 */
	public static final boolean USE_PAYLOAD_REQUESTED = false;
	public static final int CYCLES_PAYLOAD = 8;
	public static int MAX_ITEMS_PER_TYPE_PAYLOAD = 10;
	// cache for peer addresses contacted (to avoid latency from db disk accesses for plugins)
	// private static final boolean SIGN_PEER_ADDRESS_SEPARATELY = false;
	public static Hashtable<String,hds.ASNSyncRequest> peer_scheduled_requests = new Hashtable<String, ASNSyncRequest>();
	public static Hashtable<String,ArrayList<Address>> peer_contacted_addresses = new Hashtable<String, ArrayList<Address>>();
	public static Hashtable<String,ArrayList<String>> peer_contacted_dirs = new Hashtable<String, ArrayList<String>>();
	/**
	 * payload for an instance.
	 * TODO. Should be maintained to contain reset dates for organizations
	 *  since they are sent to each peer.
	 *  
	 *  When maintained, would no longer need to recompute them on each peer request
	 *  Updated each CLIENT_PAYLOAD=8 client cycles in initPayload()
	 *  
	 *  Would be good to maintain a limit on the payload.requested part!
	 * 
	 */
	public static ASNSyncPayload payload = new ASNSyncPayload();
	/**
	 * Short lived advertisement (not saved on disk).
	 * Add motions and votes here when performed.
	 * Probably should be emptied after one day?
	 * Should be managed with addToPayloadAdvertisement() 
	 * 
	 * Used in buildRequest
	 */
	public static SpecificRequest payload_recent = new SpecificRequest();
	/**
	 *  payload part (SpecificRequest advertised) present in all messages
	 *  Contains advertised constituents and motions.
	 *  
	 *  A limit is implemented with MAX_ITEMS_PER_TYPE_PAYLOAD
	 *  
	 *  TODO: should be saved and loaded from disk on startup.
	 *        should allow removal of advertisements
	 *  
	 */
	static SpecificRequest _payload_fix = new SpecificRequest();
	public static void addToPayloadFix(int type, String hash, String org_hash, int MAX_ITEM){
		_payload_fix.add(type, hash, org_hash, MAX_ITEM);
	}
	/**
	 * Called each few client cycles
	 */
	public static void initPayload() {
		 payload = new ASNSyncPayload();
	}
	/**
	 *  The place to decide on a Client1 and Client2
	 * @return
	 */
	static public IClient startClient(){
		if(DEBUG) System.out.println("ClientSync:startClient: start Client2");
		IClient r;
		r = new Client2(); // Change to Client1 on bugs
		r.start();
		if(DEBUG) System.out.println("ClientSync:startClient: started Client2");
		return r;
	}
	/**
	 * Send info got from remote directory to the display
	 * @param dir_address
	 * @param global_peer_ID
	 * @param peer_name
	 * @param da
	 * @param err
	 */
	public static void reportDa(String dir_address, String global_peer_ID, String peer_name, DirectoryAnswer da, String err){
		if(!DD.GUI) return;
		String key = "DIR:"+dir_address;
		Hashtable<String,DirectoryAnswer> old_bag = DirectoriesData.dir_data.get(key);
		if(da==null) {
			da = new DirectoryAnswer();
			da.addresses=new ArrayList<Address>();
			peer_name=peer_name+": error: "+err;
		}
		if(old_bag == null) old_bag = new Hashtable<String,DirectoryAnswer>();
		if(peer_name!=null) old_bag.put(peer_name, da);
		else  old_bag.put(global_peer_ID, da);
		DirectoriesData.dir_data.put(key, old_bag);
		if(Application.directoriesData!=null){
			Application.directoriesData.setData(DirectoriesData.dir_data);
		}else{
			if(ClientSync._DEBUG) out.println("Client:reportDa: cannot report to anybody");				
		}		
	}

	/**
	 * Adding an address to the list of recent addresses
	 * @param ad
	 * @param global_peer_ID
	 */
	static boolean add_to_peer_contacted_addresses(Address ad, String global_peer_ID) {
		ArrayList<Address> addr = ClientSync.peer_contacted_addresses.get(global_peer_ID);
		if(addr == null) addr = new ArrayList<Address>();
		if(!addr.contains(ad)) {
			if(ClientSync.DEBUG) System.out.println("\nClientSync: add_to_peer_contacted_addresses: add "+ad+" to ["+Util.concat(addr, " ", "DEF")+"]");
			addr.add(ad);
			ClientSync.peer_contacted_addresses.put(global_peer_ID, addr);
			return true;
		}
		return false;
	}

	static public boolean isMyself(int port, InetSocketAddress sock_addr, Address ad){
		//boolean DEBUG = true;
		if(ClientSync.DEBUG) out.println("ClientSync:isMyself: start");
		if(sock_addr.getPort()!=port){
			if(ClientSync.DEBUG) out.println("ClientSync: Peer has different port! "+sock_addr+"!="+port);
			return false;
		}
		String haddress = "";
		if(ClientSync.DEBUG) out.println("ClientSync:isMyself: check hostAddress");
		if(sock_addr.getAddress()!=null) haddress=sock_addr.getAddress().getHostAddress();
		if(ClientSync.DEBUG) out.println("UClient:isMyself: check hostname");
		String phost = null;
		if(ad != null) phost = ad.domain;
		if(phost == null) phost = Util.getNonBlockingHostName(sock_addr);
		if(
						phost.equals("/127.0.0.1")
						||phost.equals("localhost/127.0.0.1")
						||phost.equals("localhost")
						||phost.equals("127.0.0.1")
						||"127.0.0.1".equals(haddress)
				){
			if(ClientSync.DEBUG) out.println("ClientSync: Peer is Myself lo! "+sock_addr);
			//DD.ed.fireClientUpdate(new CommEvent(this, null, null,"FAIL ADDRESS", sock_addr+" Peer is LOOPBACK myself!"));
			return true;
		}else
			if(ClientSync.DEBUG)out.println("ClientSync: Peer is not Myself lo! \""+phost+
				"\""+haddress+"\""+port);
		//if(DEBUG) out.println("UClient:isMyself: done");
		return false;
	}

	static public boolean isMyself(int port, InetSocketAddress sock_addr, SocketAddress_Domain sad){
		if(sad == null) 
			return isMyself(port, sock_addr, sad);
		else
			return isMyself(port, sock_addr, sad.ad);
	}
	/**
	 * Add the request to the appropriate list in 
	 * ClientSync.peer_scheduled_requests
	 * @param msg
	 */
	public static void schedule_for_sending_plugin_data(PluginRequest msg) {
		if(DD.DEBUG_PLUGIN) System.out.println(CLASS+":schedule_for_sending: start");
		ASNSyncRequest value = ClientSync.peer_scheduled_requests.get(msg.peer_GID);
		if(value == null) value = new ASNSyncRequest();
		if(value.plugin_msg==null) value.plugin_msg = new D_PluginData();
		value.plugin_msg.addMsg(msg);
		ClientSync.peer_scheduled_requests.put(msg.peer_GID, value);
		if(DD.DEBUG_PLUGIN) System.out.println(CLASS+":schedule_for_sending: stop");
	}
	
	/**
	 * Called from PluginThread to immediately jumpstart sending plugin data
	 * Extracts addresses from pca and attempts to contact them
	 * 
	 * Need to also get the directories of this guy, somehow (to support NATs) :)
	 * @param msg
	 * @param pca
	 * @param peer_contacted_dirs 
	 */
	public static void try_send(PluginRequest msg, ArrayList<Address> pca) {
		if(DD.DEBUG_PLUGIN) System.out.println(CLASS+":try_send: start");
		IClient c = Application.ac;
		if(c==null){
			if(DD.DEBUG_PLUGIN) System.out.println(CLASS+":try_send: done no client");
			Application.warning(_("Plugin attempts but no client!"), _("No client"));
			return;
		}
		ArrayList<SocketAddress_Domain> tcp_sock_addresses=new ArrayList<SocketAddress_Domain>();
		ArrayList<SocketAddress_Domain> udp_sock_addresses=new ArrayList<SocketAddress_Domain>();
		ArrayList<String> peer_directories=null;
		ArrayList<InetSocketAddress> peer_directories_sockets=null;
		if(DD.DEBUG_PLUGIN) System.out.println(CLASS+":try_send: will get addresses");
		if(Application.ac instanceof Client1){
			ClientSync.getSocketAddresses(tcp_sock_addresses, udp_sock_addresses, pca, msg.peer_GID, null, null, null, null, null);
			if(DD.DEBUG_PLUGIN) System.out.println(CLASS+":try_send: got addresses");
			peer_directories = ClientSync.peer_contacted_dirs.get(msg.peer_GID);
			if(peer_directories != null) {
				peer_directories_sockets = new ArrayList<InetSocketAddress>();
				peer_directories_sockets = ClientSync.getUDPDirectoriesSockets(peer_directories, peer_directories_sockets);
			}
		}else if(Application.ac instanceof Client2){
			peer_directories = new ArrayList<String>();
			peer_directories_sockets = new ArrayList<InetSocketAddress>();
			Connections.getSocketAddresses(tcp_sock_addresses, udp_sock_addresses, 
					pca, msg.peer_GID, peer_directories, peer_directories_sockets);
		}
		if(DD.DEBUG_PLUGIN) System.out.println(CLASS+":try_send: will try_connect");
		c.try_connect(tcp_sock_addresses, udp_sock_addresses, null, null, null, msg.peer_GID, null, peer_directories_sockets, peer_directories);
		//System.out.print("c");
		if(DD.DEBUG_PLUGIN) System.out.println(CLASS+":try_send: done");
	}
	public static ArrayList<ResetOrgInfo> getChangedOrgs(String peer_ID){
		ArrayList<ResetOrgInfo> changed_orgs = null;
		try {
			/**
			 * Ideally (instead of the next line) call:
			changed_orgs = ClientSync.payload.changed_orgs.clone();
			* but which is now not maintained with new reset requests in organizations
			*/
			if(DEBUG||DD.DEBUG_CHANGED_ORGS)System.out.println("ClientSync:buildRequest: Inefficient reloading of changes (should be maintained)");
			changed_orgs = ClientSync.buildResetPayload();
			if(changed_orgs!=null){ if(DEBUG||DD.DEBUG_CHANGED_ORGS)System.out.println("ClientSync:buildRequest: changed orgs for all peers: "+Util.nullDiscrimArraySummary(changed_orgs,"--"));
			}else{ if(DEBUG||DD.DEBUG_CHANGED_ORGS)System.out.println("ClientSync:buildRequest: changed orgs for all peers: null");}
			changed_orgs = ClientSync.add_Peer_Specific_ResetDates(peer_ID, changed_orgs);
			if(changed_orgs!=null){ if(DEBUG||DD.DEBUG_CHANGED_ORGS)System.out.println("ClientSync:buildRequest: changed orgs for this peer: "+Util.nullDiscrimArraySummary(changed_orgs,"--"));
			}else{  if(DEBUG||DD.DEBUG_CHANGED_ORGS)System.out.println("ClientSync:buildRequest: changed orgs for this peer: null");}
		} catch (P2PDDSQLException e) {
			e.printStackTrace();
		}
		if(DEBUG||DD.DEBUG_CHANGED_ORGS)System.out.println("ClientSync:buildRequest: done change org");
		return changed_orgs;
	}
	/**
	 * 
	 * @param peer_ID
	 * @return
	 */
	public static ASNSyncPayload getSyncReqPayload(String peer_ID){		
		ArrayList<ResetOrgInfo> changed_orgs = getChangedOrgs(peer_ID);
		if(!ClientSync.payload_recent.empty() || !ClientSync._payload_fix.empty()
				|| ((changed_orgs != null)&&(changed_orgs.size()>0))) {
			ASNSyncPayload _payload = new ASNSyncPayload();
			if(ClientSync.USE_PAYLOAD_REQUESTED)_payload.requested.add(payload.requested);
			_payload.changed_orgs = changed_orgs;
			if(!ClientSync.payload_recent.empty() && !ClientSync._payload_fix.empty()) {
				_payload.advertised = ClientSync.payload_recent.clone();
				_payload.advertised.add(ClientSync._payload_fix);
			}
			if(DEBUG||DD.DEBUG_CHANGED_ORGS) System.out.println("Client: buildRequests: payload set");
			return _payload;
		}
		return new ASNSyncPayload();
	}
	/**
	 * Builds a request. Both versions of lastSnapshots should be provided
	 *  (but at least the string one)
	 * @param _lastSnapshotString
	 * @param _lastSnapshot
	 * @param peer_ID
	 * @return
	 */
	public static ASNSyncRequest buildRequest(String _lastSnapshotString, Calendar _lastSnapshot,
			String peer_ID) {
		if(DEBUG||DD.DEBUG_CHANGED_ORGS) System.out.println("Client: buildRequests: start: "+peer_ID);
		if(_lastSnapshot==null) _lastSnapshot =  Util.getCalendar(_lastSnapshotString);
		ASNSyncRequest sr = new ASNSyncRequest();
		if(DEBUG||DD.DEBUG_CHANGED_ORGS) out.println("lastSnapshot = "+_lastSnapshotString);
		sr.lastSnapshot = _lastSnapshot;
		sr.tableNames=SR.tableNames;
		
		sr.pushChanges = getSyncReqPayload(peer_ID);
		
		try {
			sr.request = streaming.SpecificRequest.getPeerRequest(peer_ID);
		} catch (P2PDDSQLException e) {
			e.printStackTrace();
		}
		if(DEBUG) System.out.println("Client: buildRequests: request=: "+sr);
		//sr.orgFilter=getOrgFilter();
		// version, globalID, name, slogan, creation_date, address*, broadcastable, signature_alg, served_orgs, signature*
		if(//(Application.as!=null)&&
				(Identity.getAmIBroadcastableToMyPeers())) {
			String global_peer_ID = Identity.current_peer_ID.globalID;
			if(DEBUG) System.out.println("Client: buildRequests: myself=: "+global_peer_ID);
			try {
				sr.address = D_PeerAddress.get_myself(global_peer_ID);//new D_PeerAddress(global_peer_ID, 0, true, false, true);
			} catch (Exception e) {
				e.printStackTrace();
			}
			/*
			sr.address.globalID = global_peer_ID;
			sr.address.name = Identity.current_peer_ID.name;
			sr.address.slogan = Identity.current_peer_ID.slogan;
			sr.address.creation_date = Identity.current_identity_creation_date;//Util.CalendargetInstance();
			sr.address.broadcastable = Identity.getAmIBroadcastable();
			sr.address.signature_alg = SR.HASH_ALG_V1;
			//sr.address.served_orgs = table.peer_org.getPeerOrgs(sr.address.globalID);
			//sr.address.signature = new byte[0];
			//if(SIGN_PEER_ADDRESS_SEPARATELY)
			*/
			SK sk = DD.getMyPeerSK();
			if(DEBUG)System.out.println("Client: buildRequests: will verify: "+sr.address);
			sr.address.component_basic_data.signature = sr.address.sign(sk); // not needed as encompassed by request signature
			if(DD.VERIFY_SENT_SIGNATURES||DD.VERIFY_SIGNATURE_MYPEER_IN_REQUEST) {
				if(!sr.address.verifySignature()) {
					System.err.println("Client: buildRequests: Signature failure for: "+sr.address);
					//System.err.println("Client: buildRequests: Signature failure for sk: "+sk);
					System.err.println("Client: buildRequests: Signature failure for pk: "+ciphersuits.Cipher.getPK(sr.address.component_basic_data.globalID));
				}else{
					if(DEBUG)System.out.println("Client: buildRequests: Signature success for: "+sr.address);				
				}
			}
			
			/*
			int dir_len=0;
			if(Identity.listing_directories_string!=null)
				dir_len = Identity.listing_directories_string.size();
			String my_server_address = Identity.current_server_addresses();
			if(my_server_address==null) {
				try {
					Server.detectDomain(Identity.udp_server_port);
				} catch (SocketException e) {
					e.printStackTrace();
				}
				my_server_address = Identity.current_server_addresses();
			}
			int server_addresses = 0;
			String[] server_addr = null;
			if (my_server_address!=null) {
				server_addr = my_server_address.split(Pattern.quote(DirectoryServer.ADDR_SEP));
				if (server_addr!=null) server_addresses = server_addr.length;
			}
			sr.address.address = new TypedAddress[dir_len + server_addresses];
			for(int k=0; k<dir_len; k++) {
				sr.address.address[k] = new TypedAddress();
				sr.address.address[k].address = Identity.listing_directories_string.get(k);
				sr.address.address[k].type = Server.DIR;
			}
			for(int k=0; k<server_addresses; k++) {
				sr.address.address[dir_len+k] = new TypedAddress();
				sr.address.address[dir_len+k].address = server_addr[k];
				sr.address.address[dir_len+k].type = Server.SOCKET;
			}
			*/
			
		/*
		if ((Identity.listing_directories_string!=null)&&(Identity.listing_directories_string.size()>0))
		sr.directory = new Address(Identity.listing_directories_string.get(Identity.preferred_directory_idx));
		 */
		}else{
			out.println("Not broadcastable: "+Application.as);
		}
		//sr.sign(); //signed before encoding
		try {
			if(DEBUG || DD.DEBUG_PLUGIN) System.out.println("Client: buildRequest: will get plugin data for peerID="+peer_ID);			
			sr.plugin_msg = D_PluginData.getPluginMessages(peer_ID);
			sr.plugin_info = D_PluginInfo.getRegisteredPluginInfo();
		} catch (P2PDDSQLException e) {
			e.printStackTrace();
		}
		return sr;
	}

	/**
	 * 
	 * @param ad
	 * @param key : = type+":"+s_address;
	 * @param now
	 * @param pc
	 */
	static void peerContactAdd(Address ad, String key,
			String now,
			Hashtable<String, Hashtable<String,String>> pc){
			Hashtable<String,String> value = pc.get(key);
			if(value==null){
				value = new Hashtable<String,String>();
				pc.put(key, value);
			}
			value.put(ad+"", now);
	}

	/**
	 * 
	 * @param ad
	 * @param type
	 * @param s_address
	 * @param now
	 * @param pc
	 */
	 static void peerContactAdd(Address ad, String type, String s_address,
			String now,
			Hashtable<String, Hashtable<String,String>> pc){
			String key = type+":"+s_address;
			peerContactAdd(ad, key, now, pc);
	}

	/**
	 *  parse adr_addresses and fills tcp and udp sockets,
	 *  fills report to pc
	 * @param tcp_sock_addresses
	 * @param udp_sock_addresses
	 * @param adr_addresses
	 * @param global_peer_ID
	 * @param type
	 * @param s_address
	 * @param peer_key
	 * @param now
	 * @param pc : if null, type - now are useless parameters
	 */
	static void getSocketAddresses(
			ArrayList<SocketAddress_Domain> tcp_sock_addresses,
			ArrayList<SocketAddress_Domain> udp_sock_addresses,
			ArrayList<Address> adr_addresses,
			String global_peer_ID,
			String type,
			String s_address,
			String peer_key,
			String now,
			Hashtable<String, Hashtable<String,String>> pc
			) {
		boolean DEBUG = ClientSync.DEBUG || DD.DEBUG_PLUGIN;
		if(DEBUG) System.out.println(CLASS+":getSocketAddresses: start");
		if(adr_addresses ==null){
			if(DEBUG) System.out.println(CLASS+":getSocketAddresses: null addresses");
			return;
		}
		int sizes = adr_addresses.size();
		if(DEBUG) System.out.println(CLASS+":getSocketAddresses: addresses = "+sizes+" ["+Util.concat(adr_addresses, " ", "DEF")+" ]");
		for(int k=0;k<sizes;k++) {
			Address ad = adr_addresses.get(k);
			if(DEBUG) out.print(" "+k+"+"+ad+" ");
			add_to_peer_contacted_addresses(ad, global_peer_ID);
			
			if(pc!=null) {
				if(DEBUG) System.out.println(CLASS+":getSocketAddresses:  add to peer contact");
				peerContactAdd(ad, type, s_address, now, pc);
				if(DEBUG) out.println(CLASS+":getSocketAddresses: enum d_adr="+peer_key+":"+type+":"+s_address+" val="+ad+" "+now);
			}
			InetSocketAddress ta=null,ua=null;
			try{
				if(ad.tcp_port>0)
					ta=new InetSocketAddress(ad.domain,ad.tcp_port); // can be slow
			}catch(Exception e){e.printStackTrace();}
			if(ta!=null)tcp_sock_addresses.add(new SocketAddress_Domain(ta,ad));
			try{
				if(ad.udp_port>0)
					ua=new InetSocketAddress(ad.domain,ad.udp_port); // can be slow
			}catch(Exception e){e.printStackTrace();}
			if(ua!=null)udp_sock_addresses.add(new SocketAddress_Domain(ua,ad));
			if(DEBUG) System.out.println(CLASS+":getSocketAddresses: Done handling "+ad);
		}
		if(DEBUG) out.println("");
		if(DEBUG) System.out.println(CLASS+":getSocketAddresses: done");
	}
	/**
	 * Resolve addresses for extracting UDP sockets 
	 * used for directory servers requested to help with pierceNATs if needed
	 * @param peer_directories
	 * @return
	 */
	static ArrayList<InetSocketAddress> getUDPDirectoriesSockets(
			ArrayList<String> peer_directories, ArrayList<InetSocketAddress> udp_sock_addresses) {
		for(int i=0; i<peer_directories.size(); i++) {
				String s_address = peer_directories.get(i);
				InetSocketAddress sock_addr=ClientSync.getUDPSockAddress(s_address);
			if(sock_addr!=null) udp_sock_addresses.add(sock_addr);
		}
		return udp_sock_addresses;
	}
	/**
	 * Will announce organizations that have changed such that they need resync.
	 * Will let remote know to reset their dates.
	 * @throws P2PDDSQLException
	 */
	public static void buildStaticPayload() throws P2PDDSQLException{
		payload.changed_orgs = ClientSync.buildResetPayload();
	}

	public static ArrayList<ResetOrgInfo> buildResetPayload() throws P2PDDSQLException{
		String sql = 
				"SELECT "+table.organization.reset_date+","+table.organization.global_organization_ID_hash+
				" FROM "+table.organization.TNAME+
				" WHERE "+table.organization.signature+" IS NOT NULL AND "+table.organization.reset_date+" IS NOT NULL AND "+table.organization.broadcasted+"='1';";
		ArrayList<ArrayList<Object>> a = Application.db.select(sql, new String[]{});
		if(a.size()==0) return null;
		ArrayList<ResetOrgInfo> changed_orgs = new ArrayList<ResetOrgInfo>();
		for(ArrayList<Object> o: a) {
			ResetOrgInfo roi = new ResetOrgInfo();
			roi.reset_date = Util.getCalendar(Util.getString(o.get(0)));
			roi.hash = Util.getString(o.get(1));
			changed_orgs.add(roi);
		}
		
		return changed_orgs;
	}
	/**
	 *  ChangedOrgs from OrgDistribution (resetDates) are added to payload
	 *  May use/modify input
	 * @param peer_ID
	 * @param existing 
	 * @return
	 */
	public static ArrayList<ResetOrgInfo> add_Peer_Specific_ResetDates(String peer_ID, ArrayList<ResetOrgInfo> existing) {
		if(DEBUG||DD.DEBUG_CHANGED_ORGS)System.out.println("ClientSync:add_peer_Specific_ResetDates:  start");
		ArrayList<ResetOrgInfo> s = get_Peer_Specific_ResetDates(peer_ID);
		if(s == null){
			if(DEBUG||DD.DEBUG_CHANGED_ORGS)System.out.println("ClientSync:add_peerSpecific_RD:  s null");
			return existing;
		}
		if(DEBUG||DD.DEBUG_CHANGED_ORGS)System.out.println("ClientSync:add_peerSpecific_RD: changed orgs for this peer: "+Util.nullDiscrimArraySummary(s,"--"));
		if(existing == null){
			existing = new ArrayList<ResetOrgInfo>(s);
			if(DEBUG||DD.DEBUG_CHANGED_ORGS)System.out.println("ClientSync:add_peerSpecific_RD: return s: "+Util.nullDiscrimArraySummary(existing,"--"));
			return existing;
		}
		for (ResetOrgInfo a : s){
			ResetOrgInfo old = getResetOrgInfo(existing, a.hash);
			if(old != null) {
				if(old.reset_date.before(a.reset_date))
					old.reset_date = a.reset_date;
				//else ok
			}else{
				existing.add(a);
			}
		}
		if(DEBUG||DD.DEBUG_CHANGED_ORGS)System.out.println("ClientSync:add_peerSpecific_RD: return merged: "+Util.nullDiscrimArraySummary(existing,"--"));
		return existing;
	}
	private static ResetOrgInfo getResetOrgInfo(
			ArrayList<ResetOrgInfo> changed_orgs, String hash) {
		if(changed_orgs == null) return null;
		if(hash == null) return null;
		for(ResetOrgInfo r : changed_orgs) {
			if(hash.equals(r.hash)) return r;
		}
		return null;
	}
	public static ArrayList<ResetOrgInfo> get_Peer_Specific_ResetDates(String peer_ID) {
		if(DEBUG||DD.DEBUG_CHANGED_ORGS) out.println("Client:get_Peer_Specific_ResetDates: id="+peer_ID);
		ArrayList<ResetOrgInfo> r = new ArrayList<ResetOrgInfo>();
		ArrayList<D_OrgDistribution> od =
				D_OrgDistribution.get_Org_Distribution_byPeerID(peer_ID);
		if(od == null){
			if(DEBUG||DD.DEBUG_CHANGED_ORGS) out.println("Client:get_Peer_Specific_ResetDates: od =null");
			return null;
		}else{
			if(DEBUG||DD.DEBUG_CHANGED_ORGS) out.println("Client:get_Peer_Specific_ResetDates: od!=null: #"+od.size());
		}
		for(D_OrgDistribution a: od){
			ResetOrgInfo ro = new ResetOrgInfo();
			ro.hash = a.organization_GIDhash;
			ro.reset_date = Util.getCalendar(a.reset_date);
			r.add(ro);
		}
		if(r.size() == 0){
			if(DEBUG||DD.DEBUG_CHANGED_ORGS) out.println("Client:get_Peer_Specific_ResetDates: od=null");
			return null;
		}
		return r;
	}

	/**
	 * Can be slow
	 * @param address
	 * @return
	 */
	static InetSocketAddress getUDPSockAddress(String address) {
		if(DEBUG) out.println("Client:getUDPSockAddress: getSockAddress of: "+address);
		String addresses[] = Address.split(address);
		if (addresses.length<=0){
			if(DEBUG) out.println("Client:getUDPSockAddress: Addresses length <=0 for: "+address);
			return null;
		}
		int a=Address.getUDP(addresses[0]);//.lastIndexOf(":");
		if(a<=0) a= Address.getTCP(addresses[0]);
		if(a<=0){
			if(DEBUG) out.println("Client:getUDPSockAddress: Address components !=2 for: "+addresses[0]);
			return null;
		}
		String c=Address.getDomain(addresses[0]);//.substring(0, a);
		
		if(DEBUG) out.println("Client:getUDPSockAddress: will");
		InetSocketAddress r = 
			//	InetSocketAddress.createUnresolved(c, a);
			new InetSocketAddress(c,a); // can be slow
		if(DEBUG) out.println("Client:getUDPSockAddress: done:"+r);
		return 	r;
	}
	/**
	 * Extract from database the last snapshot of peerID
	 * @param peerID
	 * @return
	 * @throws P2PDDSQLException
	 */
	public static String getLastSnapshot(String peerID) throws P2PDDSQLException {
		ArrayList<ArrayList<Object>> peers;
		peers = Application.db.select("SELECT "+table.peer.peer_ID+", "+table.peer.name+", "+table.peer.last_sync_date +
				" FROM "+ table.peer.TNAME +	
				" WHERE "+table.peer.used+" = 1 AND "+table.peer.global_peer_ID+" = ?;",
				new String[]{peerID});
		if(peers.size()<=0) return null;
		return (String)peers.get(0).get(2);
	}
	/**
	 * Add new items to advertisements
	 * this touches the client
	 * @param hash
	 * @param org_hash
	 * @param type
	 */
	public static void addToPayloadAdvertisements(String hash, String org_hash, int type) {
		RequestData target = null;
		for(RequestData a: payload_recent.rd) {
			if(org_hash.equals(a.global_organization_ID_hash)) {
				target = a;
			}
		}
		if(target == null) {
			target = new RequestData();
			payload_recent.rd.add(target);
		}
		
		if (target.addHashIfNewTo(hash, type, MAX_ITEMS_PER_TYPE_PAYLOAD))
			try {
				DD.touchClient();
			} catch (NumberFormatException e) {
				e.printStackTrace();
			} catch (P2PDDSQLException e) {
				e.printStackTrace();
			}
	}
}
package hds;

import util.Util;
import ASN1.ASN1DecoderFail;
import ASN1.Decoder;
import ASN1.Encoder;
import config.DD;

/**
 * @author msilaghi
 *
 * DAAnswer = IMPLICIT [APPLICATION 14] SEQUENCE {
 * 		result BOOLEAN,
 * 		remote_IP OCTET STRING OPTIONAL,
 *      remote_port INTEGER OPTIONAL
 * }
 */
public
class DirectoryAnnouncement_Answer extends ASN1.ASNObj{
	int version = 1;
	int[]agent_version;
	boolean result = true;
	boolean signature_required = true;
	boolean certificate_required = true;
	byte[] challenge;
	byte[] remote_IP;
	int remote_port = 0;
	
	public String toString() {
		return "D_DAAnswer: ["+result+","+Util.concat(remote_IP, ".", "?.?.?.?")+":"+remote_port+"]";
	}
	public DirectoryAnnouncement_Answer(Decoder d){
		try {
			decode(d);
		} catch (ASN1DecoderFail e) {
			e.printStackTrace();
		}
	}
	public DirectoryAnnouncement_Answer(String detected_sa) {
		if(detected_sa != null) {
			Address ad = new Address(detected_sa);
			remote_port = ad.udp_port;
			remote_IP = Util.getBytesFromCleanIPString(Util.get_IP_from_SocketAddress(ad.domain));
		}
	}
	/**
	 * TAG_AC14
	 * @return
	 */
	public static byte getASN1Type(){
		return DD.TAG_AC14;
	}
	@Override
	public Encoder getEncoder() {
		switch(version){
		case 0: return getEncoder_0();
		case 1:
		default:
			return getEncoder_1();
		}
	}
	public Encoder getEncoder_1() {
		Encoder enc = new Encoder().initSequence();
		enc.addToSequence(new Encoder(result));
		enc.addToSequence(new Encoder(version).setASN1Type(DD.TAG_AC2));
		if(!result){
			if(signature_required) enc.addToSequence(new Encoder(signature_required).setASN1Type(DD.TAG_AC3));
			if(certificate_required) enc.addToSequence(new Encoder(certificate_required).setASN1Type(DD.TAG_AC4));
		}
		if(challenge!=null) enc.addToSequence(new Encoder(challenge).setASN1Type(DD.TAG_AC5));
		if(remote_IP!=null) enc.addToSequence(new Encoder(remote_IP).setASN1Type(DD.TAG_AC6));
		if(remote_port > 0) enc.addToSequence(new Encoder(remote_port).setASN1Type(DD.TAG_AC7));
		enc.setASN1Type(getASN1Type());
		return enc;
	}
	public DirectoryAnnouncement_Answer decode_1(Decoder d) throws ASN1DecoderFail {
		if(d.isFirstObjectTagByte(DD.TAG_AC3))
			signature_required = d.getFirstObject(true).getBoolean();
		if(d.isFirstObjectTagByte(DD.TAG_AC4))
			certificate_required = d.getFirstObject(true).getBoolean();
		if(d.isFirstObjectTagByte(DD.TAG_AC5))
			challenge = d.getFirstObject(true).getBytes();
		if(d.isFirstObjectTagByte(DD.TAG_AC6))
			remote_IP = d.getFirstObject(true).getBytes();
		if(d.isFirstObjectTagByte(DD.TAG_AC7))
			remote_port = d.getFirstObject(true).getInteger().intValue();
		//if(!d.isFirstObjectTagByte(0)) throw new ASN1DecoderFail("Extra bytes in answer");
		return this;
	}	
	public Encoder getEncoder_0() {
		Encoder enc = new Encoder().initSequence();
		enc.addToSequence(new Encoder(result));
		if(remote_IP!=null) enc.addToSequence(new Encoder(remote_IP));
		if(remote_port > 0) enc.addToSequence(new Encoder(remote_port));
		enc.setASN1Type(getASN1Type());
		return enc;
	}

	@Override
	public DirectoryAnnouncement_Answer decode(Decoder dec) throws ASN1DecoderFail {
		Decoder d = dec.getContent();
		result = d.getFirstObject(true).getBoolean();
		if(d.isFirstObjectTagByte(DD.TAG_AC2)) {
			version = d.getFirstObject(true).getInteger().intValue();
		}else version = 0;
		switch(version){
		case 0:
			return decode_0(d);
		case 1:
		default:
			return decode_1(d);
		}
	}
	public DirectoryAnnouncement_Answer decode_0(Decoder d) throws ASN1DecoderFail {
		Decoder rest = d.getFirstObject(true);
		if(rest == null) return this;
		if(rest.getTypeByte() == Encoder.TAG_OCTET_STRING){
			remote_IP = rest.getBytes();
			rest = d.getFirstObject(true);
		}
		if(rest == null) return this;
		if(rest.getTypeByte() == Encoder.TAG_INTEGER){
			remote_port = rest.getInteger().intValue();
			rest = d.getFirstObject(true);
		}
		if(rest != null) throw new ASN1DecoderFail("Extra bytes in answer");
		return this;
	}
}
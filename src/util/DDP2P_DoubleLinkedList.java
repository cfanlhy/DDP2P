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
package util;

/**
 * Initialized with an empty node in head
 * @author msilaghi
 *
 * @param <T>
 */
public class DDP2P_DoubleLinkedList<T> {
	DDP2P_DoubleLinkedList_Node<T> head = new DDP2P_DoubleLinkedList_Node<T>();
	int count=0;
	public DDP2P_DoubleLinkedList(){
	}
	
	synchronized public boolean offerFirst(T crt) {
		if(crt == null) return false;
		if(!(crt instanceof DDP2P_DoubleLinkedList_Node_Payload<?>)) return false;
		@SuppressWarnings("unchecked")
		DDP2P_DoubleLinkedList_Node_Payload<T> p = (DDP2P_DoubleLinkedList_Node_Payload<T>) crt;
		if(p.get_DDP2P_DoubleLinkedList_Node()!=null) return false;

		DDP2P_DoubleLinkedList_Node<T> ins = new DDP2P_DoubleLinkedList_Node<T>(head, head.next, crt);
		head.next.previous = ins;
		head.next = ins;
		count++;
		p.set_DDP2P_DoubleLinkedList_Node(ins);
		return true;
	}

	public int size() {
		return count;
	}

	synchronized public T removeTail() {
		if(count<=0) return null;
		count--;
		DDP2P_DoubleLinkedList_Node<T> last = head.previous;
		DDP2P_DoubleLinkedList_Node<T> new_last = last.previous;
		head.previous = new_last;
		new_last.next = head;
		T crt = last.payload;

		@SuppressWarnings("unchecked")
		DDP2P_DoubleLinkedList_Node_Payload<T> p = (DDP2P_DoubleLinkedList_Node_Payload<T>) crt;
		p.set_DDP2P_DoubleLinkedList_Node(null);

		return crt;
	}
	/**
	 * 
	 * @param crt
	 */
	synchronized public void moveToFront(T crt) {
		@SuppressWarnings("unchecked")
		DDP2P_DoubleLinkedList_Node_Payload<T> p = (DDP2P_DoubleLinkedList_Node_Payload<T>) crt;
		DDP2P_DoubleLinkedList_Node<T> ins = p.get_DDP2P_DoubleLinkedList_Node();
		if(ins==null) throw new RuntimeException("Node was not in List");
		if(ins == head.next) return; //already first
		
		DDP2P_DoubleLinkedList_Node<T> prev = ins.previous;
		DDP2P_DoubleLinkedList_Node<T> next = ins.next;
		prev.next = next;
		next.previous = prev;
		
		DDP2P_DoubleLinkedList_Node<T> old_first = head.next;
		ins.next = old_first;
		ins.previous = head;
		head.next = ins;
		old_first.previous = ins;
	}

	public T getTail() {
		return head.previous.payload;
	}

	public DDP2P_DoubleLinkedList_Node<T> getHead() {
		return head;
	}

}

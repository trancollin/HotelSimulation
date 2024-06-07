import java.util.concurrent.Semaphore;
import java.util.*;

public class Hotel_Sim {
	//semaphores
	//tracks when guests are ready to be served by desk employees
	private static Semaphore guest_ready = new Semaphore(0, true);
	//tracks when guest receives room key from desk
	private static Semaphore[] room_key = new Semaphore[25];
	//tracks open spaces at the desk so guests can receive service
	private static Semaphore desk_available = new Semaphore(2, true);
	//tracks when a guest leaves the desk after receiving service
	private static Semaphore guest_leaves_desk = new Semaphore(0, true);
	//tracks if a bellhop is available to assist with bags
	private static Semaphore bellhop_available = new Semaphore(2, true);
	//used by guests to signal they need help with bags
	private static Semaphore need_bag_help = new Semaphore(0, true);
	//tracks when a guest has entered their room and is ready to receive bags from the bellhop
	private static Semaphore[] ready_for_delivery = new Semaphore[2];
	//tracks when a bellhop receives bags from a guest
	private static Semaphore[] bellhop_receives = new Semaphore[25];
	//tracks when a bellhop delivers bags to a guest
	private static Semaphore[] bellhop_delivers = new Semaphore[25];
	//tracks when a bellhop receives a tip from a guest after delivering baggage
	private static Semaphore[] tip = new Semaphore[2];
	
	//mutexes for controlling enqueueing and dequeueing
	private static Semaphore mutex1 = new Semaphore(1, true);
	private static Semaphore mutex2 = new Semaphore(1, true);
	private static Semaphore mutex3 = new Semaphore(1, true);
	private static Semaphore mutex4 = new Semaphore(1, true);
	
	//linked list to hold available rooms
	private static ArrayList<Integer> availableRooms = new ArrayList<Integer>();
	
	//queues for guests waiting_desk for service
	private static Queue<Integer> waiting_desk  = new LinkedList<>();
	private static Queue<Integer> waiting_bellhop = new LinkedList<>();
	
	//arrays for keeping track of guest interaction with desk
	private static int[] helped_by = new int[25];	//keeps track of IDs of desk/bellhop that assist a guest
	private static int[] room_number = new int[25];	//contains room numbers assigned to guests
	
	//Guest thread class
	private static class Guest implements Runnable{
		//variables
		int guestNum;
		int bags;
		int roomNum;
		
		//constructor
		Guest(int gn){
			this.guestNum = gn;
		}
		
		//sets the number of bags to random int from 0 - 5
		public void setBags() {
			bags = (int)(Math.random() * 5);
		}
		
		public void run() {
			//guest prints its creation
			System.out.println("Guest " + guestNum + " created");
			//set num of bags
			setBags();
			
			//print guest arrives at hotel
			if(bags == 1)
				System.out.println("Guest " + guestNum + " enters hotel with " + bags + " bag");
			else
				System.out.println("Guest " + guestNum + " enters hotel with " + bags + " bags");
			
			//guest waits for desk to be available
			try {
				desk_available.acquire();
			}
			catch (InterruptedException e) {}
			
			//guest signals they are ready
			try {
				mutex1.acquire();
			}
			catch(InterruptedException e) {}
			
			//guest number is enqueued
			waiting_desk.add(guestNum);
			guest_ready.release();
			mutex1.release();
			
			//wait for room key
			try {
				room_key[guestNum].acquire();
			}
			catch(InterruptedException e) {}
			
			//guest receives room key
			roomNum = room_number[guestNum];
			System.out.println("Guest " + guestNum + " receives room key for room " + roomNum +
					" from front desk employee " + helped_by[guestNum]);
			
			//guest leaves front desk
			guest_leaves_desk.release();
			
			//if guest has more than 2 bags, they request assistance
			if(bags > 2) {
				//wait for bellhop to become available
				try {
					bellhop_available.acquire();
				}
				catch(InterruptedException e) {}
				
				try {
					mutex3.acquire();
				}
				catch(InterruptedException e) {}
				//guest signals they need help with bags
				System.out.println("Guest " + guestNum + " requests help with bags");
				//guest number enqueued
				waiting_bellhop.add(guestNum);
				need_bag_help.release();
				mutex3.release();
				
				//wait for bellhop to receive bags
				try {
					bellhop_receives[guestNum].acquire();
				}
				catch(InterruptedException e) {}
				
				//guest enters room and signals they are ready for bags to be delivered
				System.out.println("Guest " + guestNum + " enters room " + roomNum);
				ready_for_delivery[helped_by[guestNum]].release();
				
				//guest waits for bags to be delivered
				try {
					bellhop_delivers[guestNum].acquire();
				}
				catch(InterruptedException e) {}
				//guest receives bag and tips
				System.out.println("Guest " + guestNum + " receives bags from bellhop " + helped_by[guestNum] +
						" and gives tip");
				tip[helped_by[guestNum]].release();	
			}
			//otherwise, guest enters room without requesting assistance
			else
				System.out.println("Guest " + guestNum + " enters room " + roomNum);
			//guest retires
			System.out.println("Guest " + guestNum + " retires for the evening");			
		}
	}
	
	private static class FrontDesk implements Runnable{
		//variables
		int deskNum;
		int currGuest;
		
		//constructor
		FrontDesk(int dn){
			this.deskNum = dn;
		}
		
		//randomly gets available room number by randomly choosing from an array list containing available rooms
		public int getRoomNum(){
			int room;
			//choose random available room
			int randomInt = (int)(Math.random() * (availableRooms.size() - 1));
			room = availableRooms.get(randomInt);
			//remove room from list of available rooms
			availableRooms.remove(randomInt);
			return room;
		}
		
		public void run() {
			//employee prints when it is created
			System.out.println("Front desk employee " + deskNum + " created");
			while(true) {
				//employee waits for customer to be ready
				try {
					guest_ready.acquire();
				}
				catch (InterruptedException e) {}
				//get room number for guest
				int room = getRoomNum();
				try {
					mutex2.acquire();
				}
				catch (InterruptedException e) {}
				//gets number of current guest
				currGuest = waiting_desk.remove();
				mutex2.release();
				//assigns guest room number
				room_number[currGuest] = room;
				//assigns deskNum to helped_by array so guest knows which employee helped them
				helped_by[currGuest] = deskNum;
				
				//employee gives room key to guest
				System.out.println("Front desk employee " + deskNum + " registers guest " + currGuest +
						" and assigns room " + room);
				room_key[currGuest].release();
				
				//employee waits for guest to leave front desk
				try {
					guest_leaves_desk.acquire();
				}
				catch(InterruptedException e) {}
				
				//employee signals front desk is open
				desk_available.release();
			}
		}
	}
	
	private static class Bellhop implements Runnable{
		//variables
		int bellhopNum;
		int currGuest;
		
		//constructor
		Bellhop(int bn){
			this.bellhopNum = bn;
		}
		
		public void run() {
			//bellhop prints its creation
			System.out.println("Bellhop " + bellhopNum + " created");
			while(true) {
				//waits for guest to request help with bags
				try {
					need_bag_help.acquire();
				}
				catch(InterruptedException e) {}
				
				try {
					mutex4.acquire();
				}
				catch(InterruptedException e) {}
				//dequeue guest number 
				currGuest = waiting_bellhop.remove();
				//set helped by to bellhopNum so guest knows which bellhop is helping them
				helped_by[currGuest] = bellhopNum;
				mutex4.release();
				//bellhop receives bags from guest
				System.out.println("Bellhop " + bellhopNum + " receives bags from guest " + currGuest);
				bellhop_receives[currGuest].release();
				
				//bellhop waits for guest to be in room before delivering bags
				try {
					ready_for_delivery[bellhopNum].acquire();
				}
				catch(InterruptedException e) {}
				
				//bellhop delivers bags to guest
				System.out.println("Bellhop " + bellhopNum + " delivers bags to guest " + currGuest);
				bellhop_delivers[currGuest].release();
				
				//bellhop waits for and accepts tip
				try {
					tip[bellhopNum].acquire();
				}
				catch(InterruptedException e) {}
				//bellhop signals they are available
				bellhop_available.release();
			}
		}
	}
	
	public static void main(String[] args) {
		//number of guests
		final int NUMGUESTS = 25;
		final int NUMFRONTDESK = 2;
		final int NUMBELLHOP = 2;
		
		//initialize room key semaphore array
		for(int l = 0; l < room_key.length; l++)
			room_key[l] = new Semaphore(0, true);
		
		//initialize ready_for_delivery semaphore array
		for(int l = 0; l < ready_for_delivery.length; l++)
			ready_for_delivery[l] = new Semaphore(0, true);
		
		//initialize bellhop_delivers semaphore array
		for(int l = 0; l < bellhop_delivers.length; l++)
			bellhop_delivers[l] = new Semaphore(0, true);
		
		//initialize bellhop_receieves semaphore array
		for(int l = 0; l < bellhop_receives.length; l++)
			bellhop_receives[l] = new Semaphore(0, true);
		
		//initialize tip semaphore array
		for(int l = 0; l < tip.length; l++)
			tip[l] = new Semaphore(0, true);
		
		//initialize availableRooms linked list
		for(int l = 1; l <= 25; l++)
			availableRooms.add(l);
		
		System.out.println("Simulation starts");
		
		//create front desk thread objects
		FrontDesk fd[] = new FrontDesk[NUMFRONTDESK];
		Thread fdThread[] = new Thread[NUMFRONTDESK];
		
		//create front desk threads
		for(int i = 0; i < NUMFRONTDESK; i++) {
			fd[i] = new FrontDesk(i);
			fdThread[i] = new Thread(fd[i]);
			//set daemon thread
			fdThread[i].setDaemon(true);
			fdThread[i].start();
		}
		
		//create bellhop thread objects
		Bellhop b[] = new Bellhop[NUMBELLHOP];
		Thread bThread[] = new Thread[NUMBELLHOP];
		
		//create bellhop threads
		for(int j = 0; j < NUMBELLHOP; j++) {
			b[j] = new Bellhop(j);
			bThread[j] = new Thread(b[j]);
			//set daemon thread
			bThread[j].setDaemon(true);
			bThread[j].start();
		}
		
		//create guest thread objects
		Guest g[] = new Guest[NUMGUESTS];
		Thread gThread[] = new Thread[NUMGUESTS];
		
		//create guest threads
		for(int k = 0; k < NUMGUESTS; k++) {
			g[k] = new Guest(k);
			gThread[k] = new Thread(g[k]);
			gThread[k].start();
		}
		
		//join guest threads
		for(int z = 0; z < NUMGUESTS; z++) {
			try {
				gThread[z].join();
			}
			catch(InterruptedException e) {}
			//print guest has joined if guest thread successfully joins
			System.out.println("Guest " + z + " joined");
		}
		//simulation ends
		System.out.println("Simulation ends");
	}
}

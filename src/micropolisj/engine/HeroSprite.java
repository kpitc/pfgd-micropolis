// This file is part of MicropolisJ.
// Copyright (C) 2013 Jason Long
// Portions Copyright (C) 1989-2007 Electronic Arts Inc.
//
// MicropolisJ is free software; you can redistribute it and/or modify
// it under the terms of the GNU GPLv3, with additional terms.
// See the README file, included in this distribution, for details.

package micropolisj.engine;
import javax.sound.sampled.*;

import static micropolisj.engine.TileConstants.RIVER;

/**
 * Implements a hero (one of the features added by KIWI).
 */
public class HeroSprite extends Sprite
{
	/*
	 * Represents the various missions a superhero can be dispatched for.
	 * Each mission corresponds to a specific disaster or state in the game.
	 * Missions:
	 * - MONSTER: hero explodes monster
	 * - FIRE: hero targets and extinguishes fires.
	 * - FLOOD: hero flies to the flood and reverses it.
	 * - MELTDOWN: (Planned) Placeholder for future nuclear disaster response.
	 * - TORNADO: hero flies to tornado and dispels it
	 * - EARTHQUAKE: (Planned) Placeholder for future earthquake response.
	 * - RETURNING: Hero is flying back to the Superhero HQ after completing a mission.
	 * - IDLE: Superhero is idle and hovering when no active mission is set. (currently not used)
	 */
	public enum HeroMission {
		MONSTER,
		FIRE,
		FLOOD,
		MELTDOWN,  //outside scope for future editing
		TORNADO,
		EARTHQUAKE,   //outside scope for future editing
		RETURNING,  //
		IDLE,
	}


	int count;
	int soundCount;
	int destX;
	int destY;
	int origX;
	int origY;
	int step;
	boolean flag; //true if the monster wants to return home

	//HERO FRAMES
	//   1...2 : idle
	//   3...4 : flying
	//   7...9 : southwest
	//  10..12 : northwest
	//      13 : north
	//      14 : east
	//      15 : south
	//      16 : west

	// movement deltas
	static int [] Gx = { 2, 2, -2, -2, 0 };
	static int [] Gy = { -2, 2, 2, -2, 0 };

	static int [] ND1 = {  0, 1, 2, 3 };
	static int [] ND2 = {  1, 2, 3, 0 };
	static int [] nn1 = {  2, 5, 8, 11 };
	static int [] nn2 = { 11, 2, 5,  8 };

	//kiwi for hovering mechanics
	int offset = 0;
	int range = 1;

	//flying mechanics
	boolean flying = false;
	boolean idle = false;
	boolean orientation = true;
	boolean attacking = false;
	int timer = 25;
	int delay = 15;

	//initializing disasters
	MonsterSprite monster;
	HeroMission mission;
	TornadoSprite tornado;
	int fireX = -1;
	int fireY = -1;
	int floodX = -1;
	int floodY = -1;

	//returning home
	boolean returnHQ = false;
	int superHQX = -1;
	int superHQY = -1;
	boolean played = false;



	public HeroSprite(Micropolis engine, int xpos, int ypos, HeroMission mission)
	{
		super(engine, SpriteKind.HER);
		this.x = xpos * 16 + 8;
		this.y = ypos * 16 + 8;

		this.width = 32;
		this.height = 32;
		this.offx = -16;
		this.offy = -16;
		this.origX = x;
		this.origY = y;

		this.count = 1000;
		CityLocation p = city.getLocationOfMaxPollution();
		this.destX = p.x * 16 + 8;
		this.destY = p.y * 16 + 8;
		this.flag = false;
		this.step = 1;

		//initialize sprites
		this.mission = mission;
		this.tornado = (TornadoSprite) engine.getSprite(SpriteKind.TOR);
		this.monster = (MonsterSprite) engine.getSprite(SpriteKind.GOD);


	}

/*
 * Updates the superhero's sprite frame to face the correct direction
 * based on the current mission and the relative position of the target.
 * The target varies depending on the mission:
 *
 *
 *
 *
 * This uses Sprite.getDir function to determine the directional
 * angle (e.g., N, NE, E, etc.) and assigns the appropriate animation frame.
 */
 private void updateFacingDirection(int dx, int dy)
	{
		//saftey check
		//if (monster == null) return;

		int targetX = 0;
		int targetY = 0;

		switch (mission)
		{
			case MONSTER:
				if (monster == null) return;
				targetX = monster.x;
				targetY = monster.y;
				break;

			case TORNADO:
				if (tornado == null) return;
				targetX = tornado.x;
				targetY = tornado.y;
				break;

			case FIRE:
				targetX = fireX;
				targetY = fireY;
				break;

			case FLOOD:
				System.out.println("changing target x and y to flood x and flood y" + mission);
				targetX = floodX;
				targetY = floodY;
				break;

			default:
				return; // no direction to face
		}

		int dir = Sprite.getDir(this.x, this.y, targetX, targetY);

		switch (dir)
		{
			case 1: // North
				this.frame = 10;
				break;
			case 2: // NE
				this.frame = 5;
				break;
			case 3: // East
				this.frame = 3;
				break;
			case 4: // SE
				this.frame = 7;
				break;
			case 5: // South
				this.frame = 9;
				break;
			case 6: // SW
				this.frame = 8;
				break;
			case 7: // West
				this.frame = 4;
				break;
			case 8: // NW
				this.frame = 6;
				break;
		}
	}

//basic hover function that (when idle) activates
	private void hover()
	{

		if (orientation) this.frame = 1;
		else this.frame = 2;

		offset += range;

		if (offset > 4 || offset < -4) {
			range *= -1;
		}

		this.y += range;

	}


/*
 * Determines superhero's attack behavior based on the current mission.
 *
 * This function uses the `attacking` flag, resets the attack delay, and updates
 * the frame based on the direction the superhero is facing.
 * does not resolve the effects of the attack
 * .
 *
 * The correct attack frame (left or right) is selected based on the `dx`
 * parameter (horizontal distance to the target).
 *
 * Missions handled:
 * - MONSTER: Prepares explosion animation.
 * - TORNADO: Prepares tornado dispersion animation.
 * - FIRE: Prepares fire extinguishing animation.
 * - FLOOD: Prepares flood reversal animation.
 *
 */
	private void attack(int dx)
	{
		System.out.println("Hero using power for mission: " + mission);

		attacking = true;
		returnHQ = false;
		delay = 15;

		orientation = dx > 0;

		switch (mission) {
			case MONSTER:
				// Stay in attack frame
				if (orientation) {
					this.frame = 11;
				} else {
					this.frame = 12;
				}
				break;

			case TORNADO:
				// Stay in attack frame
				if (orientation) {
					this.frame = 11;
				} else {
					this.frame = 12;
				}
				break;

			case FIRE:

				if (orientation) {
					this.frame = 11;
				} else {
					this.frame = 12;
				}
				break;

			case FLOOD:
				System.out.println("Attacking FLOOD");
				// Stay in attack frame
				if (orientation) {
					this.frame = 11;
				} else {
					this.frame = 12;
				}
				break;

			case MELTDOWN:
				// Stay in attack frame
				if (orientation) {
					this.frame = 11;
				} else {
					this.frame = 12;
				}
				break;

			case EARTHQUAKE:
				// Stay in attack frame
				//outside scope for future editing
				if (orientation) {
					this.frame = 11;
				} else {
					this.frame = 12;
				}
				break;

			default:
				System.out.println("Unhandled mission in usePower(): " + mission);
				break;
		}




	}

	//functions that are written for each specific disaster

	// // //MONSTER FIRE FLOOD MELTDOWN TORNADO// // //

	 //Handles the superhero's behavior during a MONSTER mission.
	 //The superhero flies toward the monster's location and calls attack
	 //Animation direction is updated en route.

	private void handleMonster() {
		System.out.println("Entered handleMonster()");


		//saftey check
		if (monster == null) return;


		flying = true;
		//tracking monster(hero flies to monster)
		if (monster == null) return; //monster is taken care of
		int dx = monster.x - this.x;
		int dy = monster.y - this.y;

		double dist = Math.sqrt((dx * dx) + (dy * dy));
		double speed = 15.0;

		if (dist > 45) {
			this.x += (int) (speed * dx / dist);
			this.y += (int) (speed * dy / dist);
			updateFacingDirection(dx, dy);
		} else {
			if (monster != null && dist < 60) {
				if (attacking == false) {
					attack(dx);
					return;
				}
			}
		}

	}

	//Handles the superhero's behavior during a TORNADO mission.
	//The superhero flies toward the tornado's location and calls attack
	//Animation direction is updated en route.
	private void handleTornado() {

		System.out.println("Entered handleTornado()");

		TornadoSprite tornado = (TornadoSprite) city.getSprite(SpriteKind.TOR);
		if (tornado == null) return;
		//tracking monster(hero flies to monster)
		flying = true;

		int dx = tornado.x - this.x;
		int dy = tornado.y - this.y;

		double dist = Math.sqrt((dx * dx) + (dy * dy));
		double speed = 15.0;

		if (dist > 20) {
			this.x += (int) (speed * dx / dist);
			this.y += (int) (speed * dy / dist);
			updateFacingDirection(dx, dy);
		} else {
			if (tornado != null && dist < 60) {
				if (attacking == false) {
					attack(dx);
					city.makeSound(tornado.x / 16, tornado.y / 16, Sound.HEROTORNADO);
					return;
				}
			}
		}
	}



	// / Handling Fires / //

	//Handles the superhero's behavior during a FIRE mission.
	//The superhero flies toward the fire's location and calls attack
	//Animation direction is updated en route.
	private void handleFire() {
		System.out.println("entering handleFire()");

		// If we don't already have a target fire location, find one
		if (fireX == -1 || fireY == -1) {
			for (int y = 0; y < city.getHeight(); y++) {
				for (int x = 0; x < city.getWidth(); x++) {
					int tile = city.getTile(x, y);
					if (tile >= TileConstants.FIRE && tile <= TileConstants.FIRE + 8) {
						fireX = x * 16 + 8;
						fireY = y * 16 + 8;
						System.out.println("Target Fire located at some x and y");
						break;
					}
				}
				if (fireX != -1) break;
			}
		}

		// in the case that we dont find fire, idle
		if (fireX == -1 || fireY == -1) {
			System.out.println("No fire found to target.");
			hover();
			return;
		}

		flying = true;

		int dx = fireX - this.x;
		int dy = fireY - this.y;

		double dist = Math.sqrt((dx * dx) + (dy * dy));
		double speed = 15.0;

		if (dist > 80) {
			this.x += (int) (speed * dx / dist);
			this.y += (int) (speed * dy / dist);
			updateFacingDirection(dx, dy);
		} else {
			if (!attacking) {
				city.makeSound(fireX / 16, fireY / 16, Sound.HEROFIRE);
				attack(dx);
				return;
			}
		}
	}

	//extinguished fires(called in handleFire
	private void extinguish() {
		for (int y = 0; y < city.getHeight(); y++) {
			for (int x = 0; x < city.getWidth(); x++) {
				int tile = city.getTile(x, y);
				if (tile >= TileConstants.FIRE && tile <= TileConstants.FIRE + 7) {
					city.setTile(x, y, TileConstants.DIRT);
				}
			}
		}
	}


	//Handles the superhero's behavior during a FLOOD mission.
	//The superhero flies toward the flood's location and calls attack
	//Animation direction is updated en route.
	private void handleFlood() {

		System.out.println("Entered handleFlood()");
		// find flooding
		if (floodX == -1 || floodY == -1) {
			for (int y = 0; y < city.getHeight(); y++) {
				for (int x = 0; x < city.getWidth(); x++) {
					int tile = city.getTile(x, y);
					if (tile >= TileConstants.FLOOD) {
						floodX = x * 16 + 8;
						floodY = y * 16 + 8;
						System.out.println("Flood located at some x and y");
						break;
					}
				}
				if (floodX != -1) break;
			}
		}

		// in the case that we dont find flood, idle
		if (floodX == -1 || floodY == -1) {
			System.out.println("No flood found to target.");
			hover();
			return;
		}
		flying = true;

		int dx = floodX - this.x;
		int dy = floodY - this.y;

		double dist = Math.sqrt((dx * dx) + (dy * dy));
		double speed = 15.0;

		if (dist > 50) {
			this.x += (int) (speed * dx / dist);
			this.y += (int) (speed * dy / dist);
			updateFacingDirection(dx, dy);
		} else {
			if (attacking == false) {
				attack(dx);
				city.makeSound(floodX / 16, floodY/ 16, Sound.HEROFLOOD);
				return;
			}
		}
	}

	//Handles the superhero's behavior during a RETURN mission.
	//The superhero flies toward the SUPERHQ's location and disappears upon arrival
	//Animation direction is updated en route.
	private void handleReturn() {
		System.out.println("Entered handleReturn()");

		// / SUPERHERO RETURNING TO HQ / //
		int dx = superHQX - this.x;
		int dy = superHQY - this.y;
		double dist = Math.sqrt((dx * dx) + (dy * dy));
		double speed = 15.0;

		if (superHQX - this.x > 0) {
			this.frame = 1;
		} else {
			this.frame = 2;
		}

		if (dist > 10) {
			this.x += (int) (speed * dx / dist);
			this.y += (int) (speed * dy / dist);
		} else {
			System.out.println("Hero has landed back at SUPERHQ");


			city.makeSound(this.x/16, this.y/16, Sound.HEROLAND);
			city.removeSprite(this);
			city.heroBusy = false;

			//check more missions in queue
			if (!city.missionQueue.isEmpty()) {
				HeroSprite.HeroMission nextMission = city.missionQueue.poll();
				city.dispatchSuperHero(nextMission);
			}
			idle = true;
		}
	}



	@Override
	public void moveImpl()
	/*
	 * Core update method for the HeroSprite, called every simulation tick.
	 *
	 * Handles all phases of superhero behavior:
	 * - what happens after hero gets to disaster location
	 * - executing appropriate behavior for mission(handle<Disaster>)
	 * - Returning to Superhero HQ after a mission is completed
	 * - Hovering when idle
	 *
	 * Updates animation
	 */
	{


		//logic for hero attacking
		if (attacking && !returnHQ) {
			timer--;

			if (delay > 0) {
				delay--;
			} else if (delay == 0) {


				switch (mission) {
					case MONSTER:
						monster.explodeSprite();
						monster = null;
						break;

					case TORNADO:
						city.removeSprite(tornado);
						city.sendMessageAt(MicropolisMessage.HERO_TORNADO_REPORT, tornado.x, tornado.y);
						break;

					case FIRE:
						int tileX = fireX / 16;
						int tileY = fireY / 16;

						//clear tile if fire
						int tile = city.getTile(tileX, tileY);
						if (tile >= TileConstants.FIRE && tile < TileConstants.FIRE + 8) {
							city.setTile(tileX, tileY, TileConstants.DIRT);
							System.out.println("Hero is putting out fires! at tile " + tileX + ", " + tileY);
						}

						extinguish();
						city.sendMessageAt(MicropolisMessage.HERO_FIRE_REPORT, fireX, fireY);
						//reset mission
						fireX = -1;
						fireY = -1;
						break;

					case FLOOD:
						city.reverseFlood();
						city.sendMessageAt(MicropolisMessage.HERO_FLOOD_REPORT, floodX, floodY);
						break;

					default:
						break;

				}
				delay = -1;
			}

			System.out.println("Attack timer: " + timer);

			if (timer <= 0) {
				attacking = false;
				flying = false;
				played = true;
				mission = HeroMission.RETURNING;

				//superhero theme
				city.makeSound(this.x / 16, this.y / 16, Sound.HEROTHEME);

				timer = 25;
				delay = 15;
			}

			return;
		}


		if (monster != null) {
			flying = true;
		}


		if (idle == true) { //superhero is idle or hovering
			hover();
			return;

		}

		//saftey check
		if (mission == null) return;

		System.out.println("current mission: " + mission);

		switch (mission) {
			case MONSTER:
				handleMonster();
				break;

			case FIRE:
				handleFire();
				break;

			case FLOOD:
				handleFlood();
				break;

			case MELTDOWN:
				//handleMeltdown();
				//outside scope for future editing
				break;

			case TORNADO:
				handleTornado();
				break;

			case EARTHQUAKE:
				//handleEarthquake();
				//outside scope for future editing
				break;

			case RETURNING:
				handleReturn();
				//outside scope for future editing
				break;

			default:
				//saftey check
				System.out.println("Unknown mission: " + mission);
				break;

		}







		}






		//System.out.println("Current frame: " + this.frame);
	}







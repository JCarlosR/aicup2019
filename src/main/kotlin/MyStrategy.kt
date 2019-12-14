import model.*
import model.Unit
import kotlin.math.abs

class MyStrategy {

    private fun getNearestEnemy(unit: Unit, game: Game): Unit? {
        var nearestEnemy: Unit? = null
        for (other in game.getOtherUnits(unit)) {
            if (nearestEnemy == null || unit.distanceTo(other.position) < unit.distanceTo(nearestEnemy.position)) {
                nearestEnemy = other
            }
        }
        return nearestEnemy
    }

    private fun getNearestWeapon(unit: Unit, game: Game)
            = getNearestLootBox(unit, game, Item.Weapon::class.java)

    private fun getNearestHealthPack(unit: Unit, game: Game)
            = getNearestLootBox(unit, game, Item.HealthPack::class.java)

    private fun getNearestLootBox(unit: Unit, game: Game, itemClass: Class<out Item>): LootBox? {
        var nearestItem: LootBox? = null

        for (lootBox in game.lootBoxes) {
            if (itemClass.isInstance(lootBox.item)) {
                if (nearestItem == null ||
                        distanceSqr(unit.position, lootBox.position) < distanceSqr(unit.position, nearestItem.position)) {
                    nearestItem = lootBox
                }
            }
        }

        return nearestItem
    }

    private fun Unit.hasWeapon() = weapon != null
    private fun Unit.hasWeapon(weaponType: WeaponType)
            = weapon?.typ == weaponType
    private fun Unit.tookDamage(game: Game) = health < game.properties.unitMaxHealth

    private fun Unit.aimTo(enemy: Unit?): Vec2Double {
        var aim = Vec2Double(0.0, 0.0)
        if (enemy == null) return aim

        aim = Vec2Double(
            enemy.position.x - position.x,
            enemy.position.y - position.y
        )

        return aim
    }

    private fun Unit.shouldSwapWeapon(nearestWeaponType: WeaponType): Boolean {
        if (weapon == null)
            return true

        // if the nearestWeapon is a Rifle, and unit doesn't have 1, take it
        val nearestRifle = nearestWeaponType == WeaponType.ASSAULT_RIFLE && weapon?.typ != WeaponType.ASSAULT_RIFLE

        if (nearestRifle || runningOutOfAmmo(1))
            return true

        return false
    }

    private fun Unit.runningOutOfAmmo(lessThanOrEqual: Int = 3): Boolean {
        val remainingBullets = weapon?.magazine ?: 0
        return remainingBullets <= lessThanOrEqual
    }

    private fun Unit.distanceTo(certainPosition: Vec2Double)
            = distanceSqr(this.position, certainPosition)

    private fun Unit.distanceFromCenter(certainPosition: Vec2Double)
            = distanceSqr(this.centerPosition(), certainPosition)

    private fun Unit.centerPosition() = Vec2Double(position.x + size.x/2, position.y + size.y/2)

    private fun Unit.shouldShoot(nearestEnemy: model.Unit?, aim: Vec2Double, game: Game): Boolean {
        if (nearestEnemy == null)
            return false

        // if enemy is on top, simply shoot
        if (nearestEnemy.isOnTopOf(this))
            return true

        // don't shoot if there is a wall in the middle
        val affectedWall = game.getNearestAffectedWall(this, aim)
        // this.hasWeapon(WeaponType.ROCKET_LAUNCHER)
        if (affectedWall != null && this.distanceTo(nearestEnemy.position) > this.distanceTo(affectedWall))
            return false

        return true
    }

    private fun Unit.isOnTopOf(unit: Unit): Boolean {
        return this.position.y > unit.position.y && abs(this.position.x - unit.position.x) < 0.7
    }

    private fun Unit.nearBullets(game: Game, weaponType: WeaponType)
            = game.bullets.filter { bullet ->
                bullet.weaponType == weaponType
                && bullet.playerId != playerId
                && distanceFromCenter(bullet.position) < NEAR_BULLETS_DISTANCE
            }

    private fun Unit.dodgeBullet(bullet: Bullet, intendedPos: Vec2Double, game: Game): Vec2Double {
        println(
            "bullet ${format(bullet.position)}, " +
            // "bSize ${format(bullet.size)}, " +
            "bVel ${format(bullet.velocity)}, " +
            // "bDmg ${bullet.damage}, " +
            "willCollision ${willCollision(bullet, game)}, " +
            "comingH ${bullet.isComingHorizontallyTo(position)}, " +
            "${jumpState.info()}, " +
            // "ground ${this.onGround}, " +
            // "ground ${this.onGround}, " +
            "falling ${this.isFalling()}"
        )

        // where is better to move?
        if (willCollision(bullet, game) && bullet.isComingHorizontallyTo(position)) {
            return Vec2Double(position.x, position.y + 1)
        }

        return intendedPos
    }

    private fun JumpState.info(): String {
        return "jumpSpeed ${this.speed}, " +
                "canCancel ${this.canCancel}, " +
                "canJump ${this.canJump}"
    }

    private fun Unit.willCollision(bullet: Bullet, game: Game, ticksToPredict: Int = 10): Boolean {
        val bulletPos = Vec2Double(bullet.position.x, bullet.position.y)
        val unitPos = Vec2Double(position.x, position.y)

        val unitDy = if (this.jumpState.speed > 0)
            this.jumpState.speed / game.properties.ticksPerSecond
        else
            this.fallingSpeed(game)

        // movement per tick
        val dx = bullet.velocity.x / game.properties.ticksPerSecond
        val dy = bullet.velocity.y / game.properties.ticksPerSecond

        // TODO: take in consideration that a wall can stop falling, and also the max jumping time can cause falling
        for (i in 1..ticksToPredict) {
            bulletPos.x += dx
            bulletPos.y += dy

            // unitPos.x canAlwaysBeReverted
            unitPos.y += unitDy

            if (this.evaluateBulletCollisionAt(unitPos, bulletPos, bullet.size))
                return true
        }

        return false
    }

    private fun Unit.collisionsWith(bullet: Bullet): Boolean {
        return this.collisionsWith(bullet.position, bullet.size)
    }

    private fun Unit.collisionsWith(bulletPos: Vec2Double, bulletSize: Double): Boolean {
        return evaluateBulletCollisionAt(this.position, bulletPos, bulletSize)
    }

    private fun Unit.evaluateBulletCollisionAt(unitEvaluatedPos: Vec2Double, bulletPos: Vec2Double, bulletSize: Double): Boolean {
        // bullet position considers the center of it
        val x1 = bulletPos.x - bulletSize/2
        val x2 = bulletPos.x + bulletSize/2
        val y1 = bulletPos.y - bulletSize/2
        val y2 = bulletPos.y + bulletSize/2

        // unit position considers the bottom middle point
        val x3 = unitEvaluatedPos.x - size.x/2
        val x4 = unitEvaluatedPos.x + size.x/2
        val y3 = unitEvaluatedPos.y
        val y4 = unitEvaluatedPos.y + size.y

        return theyCollision(x1, x2, x3, x4, y1, y2, y3, y4)
    }

    private fun theyCollision(x1: Double, x2: Double, x3: Double, x4: Double, y1: Double, y2: Double, y3: Double, y4: Double)
            = x1 < x4 && x2 > x3 && y1 < y4 && y2 > y3

    private fun Bullet.isComingHorizontallyTo(certainPosition: Vec2Double): Boolean {
        val bulletAtTheRight = certainPosition.x < position.x
        val movingToLeft = velocity.x < 0

        if (bulletAtTheRight && movingToLeft)
            return true

        val bulletAtTheLeft = position.x < certainPosition.x
        val movingToRight = velocity.x > 0

        if (bulletAtTheLeft && movingToRight)
            return true

        return false
    }

    private fun Unit.canNotMoveRight(game: Game): Boolean {
        return game.nextTileRight(this) == Tile.WALL || game.thereIsUnitAtTheRight(this)
    }

    private fun Unit.canNotMoveLeft(game: Game): Boolean {
        return game.nextTileLeft(this) == Tile.WALL || game.thereIsUnitAtTheLeft(this)
    }

    private fun Unit.shouldReload(): Boolean {
        // TODO: Consider reloadTime & opponent distance
        weapon?.let {
            if (hasWeapon() && it.lessThanHalfBullets()) {
                return true
            }
        }

        return false
    }

    private fun Unit.isFalling() = jumpState.speed == 0.0 && !jumpState.canCancel

    private fun Unit.fallingSpeed(game: Game) =
        if (this.isFalling())
            FALLING_SPEED / game.properties.ticksPerSecond
        else 0.0


    private fun Weapon.lessThanHalfBullets() = magazine < params.magazineSize / 2

    private fun Game.nextTileRight(unit: Unit)
            = level.tiles[(unit.position.x + 1).toInt()][(unit.position.y).toInt()]

    private fun Game.nextTileLeft(unit: Unit)
            = level.tiles[(unit.position.x - 1).toInt()][(unit.position.y).toInt()]

    private fun Game.getNearestAffectedWall(from: Unit, aim: Vec2Double): Vec2Double? {
        var dx = 0.0

        while (aim.x > 0 && dx <= aim.x || aim.x < 0 && dx >= aim.x) {
            val targetX = from.position.x + dx

            val dy = aim.y * dx / aim.x // same angle
            val targetY = from.position.y + dy

            val posX = targetX.toInt()
            val posY = targetY.toInt()

            val isWall =
                if (posX < level.tiles.size && posY < level.tiles[posX].size)
                    level.tiles[posX][posY] == Tile.WALL
                else false

            if (isWall) {
                // println("Nearest affected wall at $targetX, $targetY")
                return Vec2Double(targetX, targetY)
            }

            if (aim.x > 0)
                dx += 0.6
            else
                dx -= 0.6
        }
        // println(pairs)
        return null
    }

    private fun Game.getOtherUnits(excludedUnit: Unit): List<Unit> {
        return units.filter { it.playerId != excludedUnit.playerId }
    }

    private fun Game.thereIsUnitAtTheRight(mainUnit: Unit): Boolean {
        for (other in getOtherUnits(mainUnit)) {
            if (other.position.x.toInt() == mainUnit.position.x.toInt() +1) {
                return true
            }
        }
        return false
    }

    private fun Game.thereIsUnitAtTheLeft(mainUnit: Unit): Boolean {
        for (other in getOtherUnits(mainUnit)) {
            if (other.position.x.toInt() == mainUnit.position.x.toInt() -1) {
                return true
            }
        }
        return false
    }

    fun getAction(unit: Unit, game: Game, debug: Debug): UnitAction {
        print("t${game.currentTick}: ")

        val nearestEnemy = getNearestEnemy(unit, game)
        val nearestWeapon = getNearestWeapon(unit, game)
        val nearestHealthPack = getNearestHealthPack(unit, game)

        // By priority, unit prefers to:
        // 1- Look for a weapon
        // 2- Dodge Rocket Launcher bullets
        // 3- Look for a health pack / stay there
        // 4- Dodge other bullets
        // 4- Take a better weapon / swap if ammo is almost gone

        var targetPos: Vec2Double = unit.position
        var mainPurpose = "None"

        val rocketBullets = unit.nearBullets(game, WeaponType.ROCKET_LAUNCHER)

        // What's the main purpose?
        if (!unit.hasWeapon() && nearestWeapon != null) {
            targetPos = nearestWeapon.position
            mainPurpose = "Weapon"
        } else if (nearestHealthPack != null /*&& unit.tookDamage(game)*/) {
            val healthPackPos = nearestHealthPack.position
            if (healthPackPos.isOverPlatform(game))
                healthPackPos.y += 0.3
            targetPos =  healthPackPos
            mainPurpose = "Health"
        } else if (unit.runningOutOfAmmo() && nearestWeapon != null) {
            targetPos = nearestWeapon.position
            mainPurpose = "NewWeapon"
        }
        /*else if (nearestEnemy != null) {
            targetPos = nearestEnemy.position
        }*/


        var secondaryPurpose = "None"
        // Keep an eye on the Rocket bullets
        if (rocketBullets.isNotEmpty()) {
            // the main purpose position target is affected by the secondary actions
            targetPos = unit.dodgeBullet(rocketBullets.first(), targetPos, game)
            secondaryPurpose = "DodgeRocket"
        }


        val aim = unit.aimTo(nearestEnemy)

        val shoot = unit.hasWeapon() && unit.shouldShoot(nearestEnemy, aim, game)

        var swapWeapon = false
        if (nearestWeapon != null) {
            // nearestWeapon is a LootBox
            val nearestWeaponType = (nearestWeapon.item as Item.Weapon).weaponType
            swapWeapon = unit.shouldSwapWeapon(nearestWeaponType)
        }

        var jump = targetPos.y > unit.position.y
        // print("targetPos ${format(targetPos)}, unitPos ${format(unit.position)}, ")
        if (targetPos.x > unit.position.x && unit.canNotMoveRight(game)) {
            jump = true
        }
        if (targetPos.x < unit.position.x && !unit.canNotMoveLeft(game)) {
            jump = true
        }

        val reload = if (shoot) {
            false
        } else {
            unit.shouldReload()
        }

        val action = UnitAction()
        action.velocity = adjustVelocity(targetPos.x - unit.position.x)
        action.jump = jump
        action.jumpDown = !jump
        action.aim = aim
        action.shoot = shoot
        action.reload = reload
        action.swapWeapon = swapWeapon
        action.plantMine = false

        debug.draw(CustomData.Log(
        "u ${format(unit.position)}, " +
            // "aim = ${format(aim)}, " +
            "e ${format(nearestEnemy?.position ?: Vec2Double())}, " +
            "dx ${format(action.velocity)}, " +
            "h = ${unit.health}, " +
            // "shoot = $shoot, " +
            "mP ${targetInfo(mainPurpose, targetPos)}, " +
            "sP $secondaryPurpose, " +
            "t/S ${game.properties.ticksPerSecond}, "
        ))

        return action
    }

    private fun Vec2Double.isOverPlatform(game: Game): Boolean {
        return game.level.tiles[x.toInt()][y.toInt()-1] == Tile.PLATFORM
    }

    private fun adjustVelocity(velocity: Double): Double {
        val vel = abs(velocity)

        if (vel >= 2.7)
            return velocity

        if (vel >= 1.7)
            return velocity * 2

        if (vel >= 0.7)
            return velocity * 3

        if (vel >= 0.4)
            return velocity * 4

        return velocity +1
    }

    private fun targetInfo(label: String, position: Vec2Double): String {
        if (label == "None")
            return label

        return "$label ${format(position)})"
    }

    companion object {
        internal fun distanceSqr(a: Vec2Double, b: Vec2Double): Double {
            return (a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y)
        }

        const val NEAR_BULLETS_DISTANCE = 15
        const val FALLING_SPEED = -10.0 // units per Second
    }

    private fun format(v: Vec2Double): String {
        return "(${v.x.fewDecimals()}, ${v.y.fewDecimals()})"
    }

    private fun format(d: Double): String {
        return d.fewDecimals()
    }

    private fun Double.fewDecimals(): String {
        return String.format("%.2f", this)
    }
}

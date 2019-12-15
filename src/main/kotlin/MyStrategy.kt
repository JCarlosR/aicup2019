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

    private fun Unit.centerPosition() = Vec2Double(position.x, position.y + size.y/2)

    private fun Unit.shouldShoot(nearestEnemy: Unit?, aim: Vec2Double, game: Game): Boolean {
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

    private fun Unit.nearBullets(game: Game)
            = game.bullets.filter { bullet ->
                bullet.playerId != playerId
                && distanceFromCenter(bullet.position) < NEAR_BULLETS_DISTANCE
            }

    private fun Unit.dodgeBullet(bullet: Bullet, intendedPos: Vec2Double, game: Game): Vec2Double {
        // TODO: consider explosion radius only when it will really explode (against a wall)
        print(
            "bullet ${format(bullet.position)}, " +
            "bVel (${format(bullet.velocity.x/game.properties.ticksPerSecond)}, ${format(bullet.velocity.y/game.properties.ticksPerSecond)}), " +
            // "bSize ${format(bullet.size)}, " +
            // "bDmg ${bullet.damage}, " +
            "${jumpState.info()}, " +
            // "ground ${this.onGround}, " +
            // "ladder ${this.onLadder}, " +
            "falling ${this.isFalling()}, "
        )

        // moving to the intended pos causes a collision? (the intended pos could be the current pos, and it'll work)
        print("u ${format(this.position)}, intended ${format(intendedPos)}, ")
        var wasCorrected = false

        if (willCollision(bullet, game, intendedPos)) {
            print("willColl, ")

            // stop jumping to avoid the bullet
            if (this.jumpState.speed > 0 && this.jumpState.canCancel)
                correctIntendedPosY(bullet, game, intendedPos, -0.2)

            // keep jumping to avoid the bullet
            if (this.jumpState.speed > 0 && this.jumpState.canJump)
                correctIntendedPosY(bullet, game, intendedPos, +0.2)

            // try to avoid moving horizontally to the intended direction
            val wannaMoveRight = this.position.x < intendedPos.x
            if (wannaMoveRight) {
                // move to right to avoid the bullet
                if (this.canMoveRight(game))
                    wasCorrected = correctIntendedPosX(bullet, game, intendedPos, +0.5)

                // move to left to avoid the bullet
                if (!wasCorrected && this.canMoveLeft(game))
                    correctIntendedPosX(bullet, game, intendedPos, -0.5)
            } else {
                // move to left to avoid the bullet
                if (this.canMoveLeft(game))
                    wasCorrected = correctIntendedPosX(bullet, game, intendedPos, -0.5)

                // move to right to avoid the bullet
                if (!wasCorrected && this.canMoveRight(game))
                    correctIntendedPosX(bullet, game, intendedPos, +0.5)
            }

            // jump to avoid the bullet
            if (bullet.movesHorizontallyTo(this.position) && !this.isFalling())
                correctIntendedPosY(bullet, game, intendedPos, +0.5)
        }

        println()
        return intendedPos
    }

    private fun JumpState.info(): String {
        return "jumpSpeed ${this.speed}, " +
                "canCancel ${this.canCancel}, " +
                "canJump ${this.canJump}"
    }

    private fun Unit.correctIntendedPosY(bullet: Bullet, game: Game, intendedPos: Vec2Double, dyCorrection: Double): Boolean {
        print("correctY called ($dyCorrection), ")
        // decrease intendedPos.y until the unit is not affected anymore
        // there should be a limit in the tries (?)
        var dy = 0.0
        var iterations = 0

        do {
            dy += dyCorrection
            iterations += 1

            if (iterations >= MAX_ITERATIONS || dy < -game.level.tiles.size) {
                return false
            }
        } while (willCollision(bullet, game, Vec2Double(intendedPos.x, intendedPos.y + dy)))

        print("intended.y from ${format(intendedPos.y)} to ${format(intendedPos.y + dy)}, ")
        intendedPos.y += dy
        return true
    }

    private fun Unit.correctIntendedPosX(bullet: Bullet, game: Game, intendedPos: Vec2Double, dxCorrection: Double): Boolean {
        print("correctX called ($dxCorrection), ")
        // modify intendedPos.x until the unit dodges moving horizontally
        // there should be a limit in the tries (?)
        var dx = 0.0
        var iterations = 0

        do {
            dx += dxCorrection
            iterations += 1

            /*
            val blockedByWall =
                    dx > 0 && game.nextTileRight(Vec2Double(intendedPos.x+dx, intendedPos.y), Tile.WALL) ||
                    dx < 0 && game.nextTileLeft(Vec2Double(intendedPos.x+dx, intendedPos.y), Tile.WALL)
            */

            if (iterations >= MAX_ITERATIONS || dx > game.level.tiles.size) {
                // print("dx $dx, game.tiles.size ${game.level.tiles.size}, ")
                return false
            }
        } while (willCollision(bullet, game, Vec2Double(intendedPos.x +dx, intendedPos.y)))

        println("intended.x from ${format(intendedPos.x)} to ${format(intendedPos.x + dx)}, ")
        intendedPos.x += dx
        return true
    }

    private fun Unit.willCollision(bullet: Bullet, game: Game, intendedPos: Vec2Double, ticksToPredict: Int = 10): Boolean {
        val bulletPos = Vec2Double(bullet.position.x, bullet.position.y)
        val unitPos = Vec2Double(position.x, position.y)

        val unitDx = this.getVelocityX(intendedPos) / game.properties.ticksPerSecond

        val unitDy = if (this.position.y < intendedPos.y)
            this.jumpState.speed / game.properties.ticksPerSecond
        else
            this.fallingSpeed(game)

        // movement per tick
        val dx = bullet.velocity.x / game.properties.ticksPerSecond
        val dy = bullet.velocity.y / game.properties.ticksPerSecond

        // TODO: take in consideration that a wall can stop falling, and the max jumping time can cause falling
        for (i in 1..ticksToPredict) {
            bulletPos.x += dx
            bulletPos.y += dy

            unitPos.x += unitDx
            unitPos.y += unitDy

            val explosionSize = bullet.explosionParams?.radius ?: 0.0
            if (this.evaluateBulletCollisionAt(unitPos, bulletPos, bullet.size+explosionSize))
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
            = x1 <= x4 && x2 >= x3 && y1 <= y4 && y2 >= y3


    private fun Bullet.movesHorizontallyTo(certainPosition: Vec2Double): Boolean {
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


    private fun Unit.canMoveRight(game: Game): Boolean {
        return game.nextTileRight(this) != Tile.WALL && !game.thereIsUnitAtTheRight(this)
    }

    private fun Unit.canMoveLeft(game: Game): Boolean {
        return game.nextTileLeft(this) != Tile.WALL && !game.thereIsUnitAtTheLeft(this)
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

    private fun Game.nextTileRight(unit: Unit): Tile {
        var posX = (unit.position.x + 1).toInt()
        if (posX < 0) posX = 0

        var posY = unit.position.y.toInt()
        if (posY < 0) posY = 0

        return level.tiles[posX][posY]
    }


    private fun Game.nextTileLeft(unit: Unit): Tile {
        var posX = (unit.position.x - 1).toInt()
        if (posX < 0) posX = 0

        var posY = unit.position.y.toInt()
        if (posY < 0) posY = 0

        return level.tiles[posX][posY]
    }


    private fun Game.nextTileRight(position: Vec2Double, tileType: Tile)
            = level.tiles[(position.x + 1).toInt()][(position.y).toInt()] == tileType

    private fun Game.nextTileLeft(position: Vec2Double, tileType: Tile)
            = level.tiles[(position.x - 1).toInt()][(position.y).toInt()] == tileType


    private fun Game.getNearestAffectedWall(from: Unit, aim: Vec2Double): Vec2Double? {
        var dx = 0.0

        while (aim.x > 0 && dx <= aim.x || aim.x < 0 && dx >= aim.x) {
            val targetX = from.position.x + dx

            val dy = aim.y * dx / aim.x // same angle
            val targetY = from.position.y + dy

            val posX = targetX.toInt()
            val posY = targetY.toInt()

            val isWall =
                if (posX >= 0 && posX < level.tiles.size && posY >=0 && posY < level.tiles[posX].size)
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
        val nearBullets = if (rocketBullets.isEmpty()) unit.nearBullets(game) else listOf()

        var swapWeapon = false
        if (nearestWeapon != null) {
            // nearestWeapon is a LootBox
            val nearestWeaponType = (nearestWeapon.item as Item.Weapon).weaponType
            swapWeapon = unit.shouldSwapWeapon(nearestWeaponType)
        }

        // What's the main purpose?
        if (!unit.hasWeapon() && nearestWeapon != null) {
            targetPos = nearestWeapon.position
            mainPurpose = "Weapon"
        } else if (nearestHealthPack != null /*&& unit.tookDamage(game)*/) {
            val healthPackPos = nearestHealthPack.position
            mainPurpose = "Health"

            if (healthPackPos.isOverPlatform(game)) {
                healthPackPos.y += 0.3
                mainPurpose += "Platform"
            }

            targetPos =  healthPackPos

        } else if (nearestWeapon != null && (unit.runningOutOfAmmo() || swapWeapon)) {
            targetPos = nearestWeapon.position
            mainPurpose = "NewWeapon"

        }/* else if (nearestEnemy != null) {
            targetPos = nearestEnemy.position
        }*/


        var secondaryPurpose = "None"

        // Keep an eye on the Rocket bullets & the rest only if there is no purpose
        if (rocketBullets.isNotEmpty()) {
            // the main position target is affected by this
            targetPos = unit.dodgeBullet(rocketBullets.first(), targetPos, game)
            secondaryPurpose = "DodgeRocket"
        } else if (nearBullets.isNotEmpty()) {
            targetPos = unit.dodgeBullet(nearBullets.first(), targetPos, game)
            secondaryPurpose = "DodgeOther"
        }


        val aim = unit.aimTo(nearestEnemy)

        val shoot = unit.hasWeapon() && unit.shouldShoot(nearestEnemy, aim, game)


        var jump = targetPos.y > unit.position.y
        // print("targetPos ${format(targetPos)}, unitPos ${format(unit.position)}, ")
        if (unit.position.x < targetPos.x && !unit.canMoveRight(game)) {
            jump = true
        }
        if (targetPos.x < unit.position.x && !unit.canMoveLeft(game)) {
            jump = true
        }

        val reload = if (shoot) {
            false
        } else {
            unit.shouldReload()
        }

        val action = UnitAction()
        action.velocity = unit.getVelocityX(targetPos)
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
            "mP $mainPurpose, " +
            "sP $secondaryPurpose, " +
            "targetPos = ${format(targetPos)}, " +
            "t/S ${game.properties.ticksPerSecond}, "
        ))

        return action
    }

    private fun Unit.getVelocityX(targetPos: Vec2Double): Double {
        val diff = targetPos.x - this.position.x

        // if the target position is at the same Y tile, accelerate fast
        if (targetPos.y.toInt() == this.position.y.toInt() && this.position.y >= targetPos.y)
            return adjustVelocity(diff)

        // otherwise, accelerate but not so much
        return adjustVelocity(diff, false)
    }

    private fun Vec2Double.isOverPlatform(game: Game): Boolean {
        return game.level.tiles[x.toInt()][y.toInt()-1] == Tile.PLATFORM
    }

    private fun adjustVelocity(velocity: Double, fast: Boolean = true): Double {
        val vel = abs(velocity)
        val factor = if (velocity > 0) +1 else -1

        if (vel >= 2.7)
            return velocity

        if (vel >= 1.7)
            return velocity * 2

        if (vel >= 0.7)
            return velocity * 3

        if (vel >= 0.4)
            return if (fast)
                velocity * 4
            else velocity + factor * 0.4

        return if (fast)
            velocity + factor * 1.4
        else
            velocity + factor * 0.5
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

        const val NEAR_BULLETS_DISTANCE = 24
        const val FALLING_SPEED = -10.0 // units per Second
        const val MAX_ITERATIONS = 100
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

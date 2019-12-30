package mindustry.entities.type.base;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.Array;
import arc.util.*;
import mindustry.*;
import mindustry.ai.Pathfinder.*;
import mindustry.entities.*;
import mindustry.entities.bullet.*;
import mindustry.entities.type.*;
import mindustry.entities.units.*;
import mindustry.game.*;
import mindustry.input.Placement;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.*;
import mindustry.world.meta.*;

import static mindustry.Vars.*;

public class GroundUnit extends BaseUnit{
    protected static Vec2 vec = new Vec2();

    protected float walkTime;
    protected float stuckTime;
    protected float baseRotation;

    public final UnitState

    attack = new UnitState(){
        public void entered(){
            target = null;
        }

        public void update(){
            TileEntity core = getClosestEnemyCore();

            if(core == null){
                Tile closestSpawn = getClosestSpawner();
                if(closestSpawn == null || !withinDst(closestSpawn, Vars.state.rules.dropZoneRadius + 85f)){
                    moveToCore(PathTarget.enemyCores);
                }
            }else{

                float dst = dst(core);

                if(dst < getWeapon().bullet.range() / 1.1f){
                    target = core;
                }

                if(dst > getWeapon().bullet.range() * 0.5f){
                    moveToCore(PathTarget.enemyCores);
                }
            }
        }
    },
    rally = new UnitState(){
        public void update(){
            Tile target = getClosest(BlockFlag.rally);

            if(target != null && dst(target) > 80f){
                moveToCore(PathTarget.rallyPoints);
            }
        }
    },
    retreat = new UnitState(){
        public void entered(){
            target = null;
        }

        public void update(){
            moveAwayFromCore();
        }
    };
    @Override
    public void override(){
        if(overrider == null) {
            overrider = new UnitOverrider(this);
            setState(overrider);
        }
    }
    public static class UnitOverrider extends BaseUnit.UnitOverrider{
        GroundUnit unit;
        Point2 target = new Point2(Integer.MIN_VALUE, Integer.MIN_VALUE);
        Array<Point2> path = new Array<>();
        int pathIndex = 0;
        public UnitOverrider(GroundUnit unit) {
            super(unit);
            this.unit = unit;
        }
        @Override
        public void entered() {
            unit.target = null;
        }
        @Override
        public void update() {
            while (pathIndex < path.size){
                Point2 point = path.get(pathIndex);
                if(point.equals(unit.tileX(), unit.tileY())){
                    ++pathIndex;
                } else {
                    unit.velocity.add(vec.trns(unit.angleTo(point.x * tilesize, point.y * tilesize), unit.type.speed * Time.delta()));
                    unit.rotation = Mathf.slerpDelta(unit.rotation, unit.baseRotation, unit.type.rotatespeed);
                    break;
                }
            }
        }
        @Override
        public void moveTo(float x, float y){
            int newX = world.toTile(x);
            int newY = world.toTile(y);
            if(!target.equals(newX, newY)) {
                Array<Point2> newPath = Placement.unitPathfind(unit.tileX(), unit.tileY(), newX, newY);
                path.clear();
                if(newPath != null){
                    target.set(newX, newY);
                    path = newPath.map(Point2::cpy);
                }
                pathIndex = 0;
            }
        }
    }
    /*
    @Override
    public void drawUnder() {
        super.drawUnder();
        if(overrider != null){
            Lines.beginLine();
            Lines.stroke(tilesize / 4f, Color.scarlet);
            Point2 prev = null;
            for(Point2 point: ((UnitOverrider) overrider).path){
                if(prev != null){
                    Lines.line(prev.x * tilesize, prev.y * tilesize, point.x * tilesize, point.y * tilesize);
                }
                prev = point;
            }
            Lines.endLine();
        }
    }*/

    @Override
    public void onCommand(UnitCommand command){
        state.set(command == UnitCommand.retreat ? retreat :
        command == UnitCommand.attack ? attack :
        command == UnitCommand.rally ? rally :
        null);
    }

    @Override
    public void interpolate(){
        super.interpolate();

        if(interpolator.values.length > 1){
            baseRotation = interpolator.values[1];
        }
    }

    @Override
    public void move(float x, float y){
        float dst = Mathf.dst(x, y);
        if(dst > 0.01f){
            baseRotation = Mathf.slerp(baseRotation, Mathf.angle(x, y), type.baseRotateSpeed * (dst / type.speed));
        }
        super.move(x, y);
    }

    @Override
    public UnitState getStartState(){
        return attack;
    }

    @Override
    public void update(){
        super.update();

        stuckTime = !vec.set(x, y).sub(lastPosition()).isZero(0.0001f) ? 0f : stuckTime + Time.delta();

        if(!velocity.isZero()){
            baseRotation = Mathf.slerpDelta(baseRotation, velocity.angle(), 0.05f);
        }

        if(stuckTime < 1f){
            walkTime += Time.delta();
        }
    }

    @Override
    public Weapon getWeapon(){
        return type.weapon;
    }

    @Override
    public void draw(){
        Draw.mixcol(Color.white, hitTime / hitDuration);

        float ft = Mathf.sin(walkTime * type.speed * 5f, 6f, 2f + type.hitsize / 15f);

        Floor floor = getFloorOn();

        if(floor.isLiquid){
            Draw.color(Color.white, floor.color, 0.5f);
        }

        for(int i : Mathf.signs){
            Draw.rect(type.legRegion,
            x + Angles.trnsx(baseRotation, ft * i),
            y + Angles.trnsy(baseRotation, ft * i),
            type.legRegion.getWidth() * i * Draw.scl, type.legRegion.getHeight() * Draw.scl - Mathf.clamp(ft * i, 0, 2), baseRotation - 90);
        }

        if(floor.isLiquid){
            Draw.color(Color.white, floor.color, drownTime * 0.4f);
        }else{
            Draw.color(Color.white);
        }

        Draw.rect(type.baseRegion, x, y, baseRotation - 90);

        Draw.rect(type.region, x, y, rotation - 90);

        for(int i : Mathf.signs){
            float tra = rotation - 90, trY = -type.weapon.getRecoil(this, i > 0) + type.weaponOffsetY;
            float w = -i * type.weapon.region.getWidth() * Draw.scl;
            Draw.rect(type.weapon.region,
            x + Angles.trnsx(tra, getWeapon().width * i, trY),
            y + Angles.trnsy(tra, getWeapon().width * i, trY), w, type.weapon.region.getHeight() * Draw.scl, rotation - 90);
        }

        Draw.mixcol();
    }

    @Override
    public void behavior(){

        if(!Units.invalidateTarget(target, this)){
            if(dst(target) < getWeapon().bullet.range()){
                rotate(angleTo(target));

                if(Angles.near(angleTo(target), rotation, 13f)){
                    BulletType ammo = getWeapon().bullet;

                    Vec2 to = Predict.intercept(GroundUnit.this, target, ammo.speed);

                    getWeapon().update(GroundUnit.this, to.x, to.y);
                }
            }
        }
    }

    @Override
    public void updateTargeting(){
        super.updateTargeting();

        if(Units.invalidateTarget(target, team, x, y, Float.MAX_VALUE)){
            target = null;
        }

        if(retarget()){
            targetClosest();
        }
    }

    protected void patrol(){
        vec.trns(baseRotation, type.speed * Time.delta());
        velocity.add(vec.x, vec.y);
        vec.trns(baseRotation, type.hitsizeTile * 5);
        Tile tile = world.tileWorld(x + vec.x, y + vec.y);
        if((tile == null || tile.solid() || tile.floor().drownTime > 0 || tile.floor().isLiquid) || stuckTime > 10f){
            baseRotation += Mathf.sign(id % 2 - 0.5f) * Time.delta() * 3f;
        }

        rotation = Mathf.slerpDelta(rotation, velocity.angle(), type.rotatespeed);
    }

    protected void circle(float circleLength){
        if(target == null) return;

        vec.set(target.getX() - x, target.getY() - y);

        if(vec.len() < circleLength){
            vec.rotate((circleLength - vec.len()) / circleLength * 180f);
        }

        vec.setLength(type.speed * Time.delta());

        velocity.add(vec);
    }

    protected void moveToCore(PathTarget path){
        Tile tile = world.tileWorld(x, y);
        if(tile == null) return;
        Tile targetTile = pathfinder.getTargetTile(tile, team, path);

        if(tile == targetTile) return;

        velocity.add(vec.trns(angleTo(targetTile), type.speed * Time.delta()));
        if(Units.invalidateTarget(target, this)){
            rotation = Mathf.slerpDelta(rotation, baseRotation, type.rotatespeed);
        }
    }

    protected void moveAwayFromCore(){
        Team enemy = null;
        for(Team team : team.enemies()){
            if(team.active()){
                enemy = team;
                break;
            }
        }

        if(enemy == null){
            for(Team team : team.enemies()){
                enemy = team;
                break;
            }
        }

        if(enemy == null) return;

        Tile tile = world.tileWorld(x, y);
        if(tile == null) return;
        Tile targetTile = pathfinder.getTargetTile(tile, enemy, PathTarget.enemyCores);
        TileEntity core = getClosestCore();

        if(tile == targetTile || core == null || dst(core) < 120f) return;

        velocity.add(vec.trns(angleTo(targetTile), type.speed * Time.delta()));
        rotation = Mathf.slerpDelta(rotation, baseRotation, type.rotatespeed);
    }
}

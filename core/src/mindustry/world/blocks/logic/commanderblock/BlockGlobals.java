package mindustry.world.blocks.logic.commanderblock;

import arc.func.Func2;
import arc.math.Mathf;
import mindustry.content.Fx;
import mindustry.content.UnitTypes;
import mindustry.entities.Effects;
import mindustry.entities.Units;
import mindustry.entities.type.BaseUnit;
import mindustry.type.Item;
import mindustry.type.UnitType;
import mindustry.world.blocks.logic.DroneCommanderBlock;
import mindustry.world.blocks.logic.commanderblock.interpreter.Globals;
import mindustry.world.blocks.logic.commanderblock.interpreter.Interpreter;
import mindustry.world.blocks.logic.commanderblock.interpreter.InterpreterObject;
import mindustry.world.blocks.logic.commanderblock.nodes.NativeFunction;

import static mindustry.Vars.content;
import static mindustry.Vars.unitGroup;

public class BlockGlobals {
    public Debug_ debug;
    public Block_ block;
    public Unit_ unit;
    public Item_ item;
    public BlockGlobals(Interpreter interpreter, DroneCommanderBlock.DroneCommanderBlockEntity entity){
        debug = new Debug_(interpreter, entity);
        block = new Block_(interpreter, entity);
        unit = new Unit_(interpreter, entity);
        item = new Item_(interpreter, entity);
    }
    public void modifyGlobals(InterpreterObject global){
        global.setProperty(InterpreterObject.create("Debug"), debug.global());
        global.setProperty(InterpreterObject.create("Block"), block.global());
        global.setProperty(InterpreterObject.create("Unit"), unit.global());
        global.setProperty(InterpreterObject.create("Items"), item.global());
    }
    public abstract static class BlockGlobal extends Globals.Global {
        public DroneCommanderBlock.DroneCommanderBlockEntity entity;
        public BlockGlobal(Interpreter interpreter, DroneCommanderBlock.DroneCommanderBlockEntity entity) {
            super(interpreter);
            this.entity = entity;
        }
    }
    public static class Debug_ extends BlockGlobal {
        public Debug_(Interpreter interpreter, DroneCommanderBlock.DroneCommanderBlockEntity entity) {
            super(interpreter, entity);
        }
        public InterpreterObject global(){
            InterpreterObject obj = InterpreterObject.create();
            obj.setProperty(InterpreterObject.create("effect"), InterpreterObject.create(new NativeFunction(this::effect)));
            return obj;
        }
        public InterpreterObject effect(InterpreterObject[] args){
            if(args.length == 2){
                Object x = args[0].value();
                Object y = args[1].value();
                if(x instanceof Float && y instanceof Float){
                    Effects.effect(Fx.blastsmoke, (float) x, (float) y);
                }
            }
            return InterpreterObject.nullObject;
        }
    }
    public static class Block_ extends BlockGlobal {
        public Block_(Interpreter interpreter, DroneCommanderBlock.DroneCommanderBlockEntity entity) {
            super(interpreter, entity);
        }
        public InterpreterObject global(){
            InterpreterObject obj = InterpreterObject.create();
            obj.setProperty(InterpreterObject.create("sleep"), InterpreterObject.create(new NativeFunction(this::sleep)));
            obj.setProperty(InterpreterObject.create("x"), InterpreterObject.create(entity.x));
            obj.setProperty(InterpreterObject.create("y"), InterpreterObject.create(entity.y));
            return obj;
        }
        public InterpreterObject sleep(InterpreterObject[] args){
            entity.sleepCycles = 1;
            if(args.length > 0){
                Object value = args[0].value();
                if(value instanceof Float) {
                    entity.sleepCycles = Mathf.floorPositive((float) value);
                }
            }
            if(entity.sleepCycles < 0){
                entity.sleepCycles = 0;
            }
            return InterpreterObject.nullObject;
        }
    }
    public static class Item_ extends BlockGlobal {
        public Item_(Interpreter interpreter, DroneCommanderBlock.DroneCommanderBlockEntity entity) {
            super(interpreter, entity);
        }
        public InterpreterObject global(){
            InterpreterObject obj = InterpreterObject.create();
            for(Item item: content.items()){
                obj.setProperty(InterpreterObject.create(item.name), InterpreterObject.create((float) item.id));
            }
            return obj;
        }
    }
    public static class Unit_ extends BlockGlobal {
        public InterpreterObject unitType = InterpreterObject.create("type");
        public InterpreterObject unitX = InterpreterObject.create("x");
        public InterpreterObject unitY = InterpreterObject.create("y");
        public InterpreterObject unitKill = InterpreterObject.create("kill");
        public InterpreterObject unitOverride = InterpreterObject.create("override");
        public InterpreterObject unitReset = InterpreterObject.create("reset");
        public InterpreterObject unitMoveTo = InterpreterObject.create("move_to");
        public InterpreterObject unitShootAt = InterpreterObject.create("shoot_at");
        public InterpreterObject unitLoadFrom = InterpreterObject.create("load_from");
        public InterpreterObject unitUnloadTo = InterpreterObject.create("unload_to");
        public Unit_(Interpreter interpreter, DroneCommanderBlock.DroneCommanderBlockEntity entity) {
            super(interpreter, entity);
        }
        public InterpreterObject global(){
            InterpreterObject obj = InterpreterObject.create();
            obj.setProperty(InterpreterObject.create("units"), InterpreterObject.create(new NativeFunction(this::units)));
            obj.setProperty(InterpreterObject.create("type"), typeObject());
            return obj;
        }
        private InterpreterObject typeObject(){
            InterpreterObject obj = InterpreterObject.create();
            for(UnitType type: content.units()){
                obj.setProperty(InterpreterObject.create(type.name), InterpreterObject.create((float) type.id));
            }
            return obj;
        }
        public InterpreterObject units(InterpreterObject[] args){
            InterpreterObject list = interpreter.globals.list.create(null);
            NativeFunction push = (NativeFunction) list.getProperty(Globals.List_.listPush).value();
            InterpreterObject[] unitObj = new InterpreterObject[1];
            Units.each(entity.getTeam(), unit -> {
                unitObj[0] = createUnitObject(unit);
                push.func.get(unitObj);
            });
            return list;
        }
        private InterpreterObject createUnitObject(BaseUnit unit){
            InterpreterObject unitObj = InterpreterObject.create();
            unitObj.setProperty(unitType, InterpreterObject.create((float) unit.getType().id));
            unitObj.setProperty(unitX, InterpreterObject.create(new NativeFunction(a ->
                    unit.isValid() ? InterpreterObject.create(unit.x) : InterpreterObject.nullObject))
            );
            unitObj.setProperty(unitY, InterpreterObject.create(new NativeFunction(a ->
                    unit.isValid() ? InterpreterObject.create(unit.y) : InterpreterObject.nullObject))
            );
            unitObj.setProperty(unitKill, InterpreterObject.create(new NativeFunction(a -> {
                if(unit.isValid()) unit.kill();
                return InterpreterObject.nullObject;
            })));
            unitObj.setProperty(unitOverride, InterpreterObject.create(new NativeFunction(a -> {
                if(unit.isValid()) unit.override();
                return InterpreterObject.nullObject;
            })));
            unitObj.setProperty(unitReset, InterpreterObject.create(new NativeFunction(a -> {
                if(unit.isValid()) unit.reset();
                return InterpreterObject.nullObject;
            })));
            unitObj.setProperty(unitMoveTo, wrapOverriderTwoFloatsFunc(unit, (x, y) -> {
                unit.overrider.moveTo(x, y);
                return null;
            }));
            unitObj.setProperty(unitShootAt, wrapOverriderTwoFloatsFunc(unit, (x, y) -> {
                unit.overrider.shootAt(x, y);
                return null;
            }));
            unitObj.setProperty(unitLoadFrom, InterpreterObject.create(new NativeFunction(args -> {
                if(unit.isValid() && args.length == 3 && unit.overrider != null){
                    Object x = args[0].value();
                    Object y = args[1].value();
                    Object id = args[2].value();
                    if(x instanceof Float && y instanceof Float && id instanceof Float) {
                        Item item = content.item(Mathf.floor((float) id));
                        if(item != null){
                            unit.overrider.loadFrom((float) x, (float) y, item);
                        }
                    }
                }
                return InterpreterObject.nullObject;
            })));
            unitObj.setProperty(unitUnloadTo, wrapOverriderTwoFloatsFunc(unit, (x, y) -> {
                unit.overrider.unloadTo(x, y);
                return null;
            }));
            return unitObj;
        }
        private InterpreterObject wrapOverriderTwoFloatsFunc(BaseUnit unit, Func2<Float, Float, Void> func){
            return InterpreterObject.create(new NativeFunction(args -> {
                if(unit.isValid() && args.length == 2 && unit.overrider != null){
                    Object a = args[0].value();
                    Object b = args[1].value();
                    if(a instanceof Float && b instanceof Float) {
                        func.get((float) a, (float) b);
                    }
                }
                return InterpreterObject.nullObject;
            }));
        }
    }
}

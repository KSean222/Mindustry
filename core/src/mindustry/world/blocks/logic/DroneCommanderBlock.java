package mindustry.world.blocks.logic;

import mindustry.annotations.Annotations.*;
import arc.*;
import arc.Input.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.Mathf;
import arc.math.geom.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import arc.util.pooling.*;
import mindustry.entities.*;
import mindustry.entities.effect.Fire;
import mindustry.entities.type.*;
import mindustry.gen.*;
import mindustry.net.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;
import mindustry.world.*;
import mindustry.world.blocks.logic.commanderblock.BlockGlobals;
import mindustry.world.blocks.logic.commanderblock.ui.CodeTextArea;
import mindustry.world.blocks.logic.commanderblock.interpreter.Interpreter;
import mindustry.world.blocks.logic.commanderblock.parser.CharStream;
import mindustry.world.blocks.logic.commanderblock.parser.Parser;
import mindustry.world.blocks.logic.commanderblock.parser.TokenStream;
import mindustry.world.blocks.logic.commanderblock.nodes.Codeblock;

import java.io.*;

import static mindustry.Vars.*;

public class DroneCommanderBlock extends Block{
    protected static int maxTextLength = 22000;
    protected static int maxNewlines = 2400;

    public DroneCommanderBlock(String name){
        super(name);
        update = true;
        configurable = true;
        solid = true;
        destructible = true;
        entityType = DroneCommanderBlockEntity::new;
    }

    @Remote(targets = Loc.both, called = Loc.both, forward = true)
    public static void setDroneCommanderBlockText(Player player, Tile tile, String text){
        if(!Units.canInteract(player, tile)) return;
        if(net.server() && text.length() > maxTextLength){
            throw new ValidateException(player, "Player has gone above text limit.");
        }

        //can be broken while a player is typing
        if(!(tile.block() instanceof DroneCommanderBlock)){
            return;
        }

        StringBuilder result = new StringBuilder(text.length());
        text = text.trim();
        int count = 0;
        for(int i = 0; i < text.length(); i++){
            char c = text.charAt(i);
            if(c == '\n' || c == '\r'){
                count ++;
                if(count <= maxNewlines){
                    result.append('\n');
                }
            }else{
                result.append(c);
            }
        }

        DroneCommanderBlockEntity entity = tile.ent();
        if(entity != null){
            entity.setMessage(result.toString());
        }
    }

    @Override
    public void drawSelect(Tile tile){
        DroneCommanderBlockEntity entity = tile.ent();
        BitmapFont font = Fonts.outline;
        GlyphLayout l = Pools.obtain(GlyphLayout.class, GlyphLayout::new);
        boolean ints = font.usesIntegerPositions();
        font.getData().setScale(1 / 4f / Scl.scl(1f));
        font.setUseIntegerPositions(false);

        String text = entity.getPreviewMessage() == null || entity.getPreviewMessage().isEmpty() ? "[lightgray]" + Core.bundle.get("empty") : entity.getPreviewMessage();

        l.setText(font, text, Color.white, 90f, Align.left, true);
        float offset = 1f;

        Draw.color(0f, 0f, 0f, 0.2f);
        Fill.rect(tile.drawx(), tile.drawy() - tilesize/2f - l.height/2f - offset, l.width + offset*2f, l.height + offset*2f);
        Draw.color();
        font.setColor(Color.white);
        font.draw(text, tile.drawx() - l.width/2f, tile.drawy() - tilesize/2f - offset, 90f, Align.left, true);
        font.setUseIntegerPositions(ints);

        font.getData().setScale(1f);

        Pools.free(l);
    }

    @Override
    public void buildConfiguration(Tile tile, Table table){
        DroneCommanderBlockEntity entity = tile.ent();

        table.addImageButton(Icon.pencilSmall, () -> {
            if(mobile){
                Core.input.getTextInput(new TextInput(){{
                    text = entity.getMessage();
                    multiline = true;
                    maxLength = maxTextLength;
                    accepted = out -> {
                        Call.setDroneCommanderBlockText(player, tile, out);
                    };
                }});
            }else{
                FloatingDialog dialog = new FloatingDialog("$editmessage");
                dialog.setFillParent(false);
                CodeTextArea a = dialog.cont.add(new CodeTextArea(entity.getMessage().replace("\n", "\r"))).size(760f, 320f).get();
                a.setFilter((textField, c) -> {
                    if(c == '\n' || c == '\r'){
                        int count = 0;
                        for(int i = 0; i < textField.getText().length(); i++){
                            if(textField.getText().charAt(i) == '\n' || textField.getText().charAt(i) == '\r'){
                                count++;
                            }
                        }
                        return count < maxNewlines;
                    }
                    return true;
                });
                a.setMaxLength(maxTextLength);
                dialog.buttons.addButton("$ok", () -> {
                    Call.setDroneCommanderBlockText(player, tile, a.getText());
                    dialog.hide();
                }).size(130f, 60f);
                dialog.update(() -> {
                    if(!entity.isValid()){
                        dialog.hide();
                    }
                });
                dialog.show();
            }
            control.input.frag.config.hideConfig();
        }).size(40f);
    }

    @Override
    public void updateTableAlign(Tile tile, Table table){
        Vec2 pos = Core.input.mouseScreen(tile.drawx(), tile.drawy() + tile.block().size * tilesize / 2f + 1);
        table.setPosition(pos.x, pos.y, Align.bottom);
    }

    public static class DroneCommanderBlockEntity extends TileEntity{
        private String message;
        private String errorMsg;
        private Interpreter interpreter;
        public final int maxStepsPerCycle = 5000;
        public int sleepCycles = 0;
        private boolean running = false;
        public DroneCommanderBlockEntity(){
            super();
            setMessage("foo = 0;");
        }
        @Override
        public void update() {
            super.update();
            int executeAmount = Mathf.round(delta());
            while(executeAmount-- > 0){
                if(sleepCycles > 0){
                    --sleepCycles;
                } else {
                    int steps = 0;
                    while(sleepCycles == 0){
                        if(!running) break;
                        try {
                            running = interpreter.step();
                        } catch(Interpreter.RuntimeError e) {
                            handleError(e, -1, -1);
                        }
                        if(++steps > maxStepsPerCycle){
                            Fire.create(tile);
                            break;
                        }
                    }
                }
            }
        }

        @Override
        public void write(DataOutput stream) throws IOException{
            super.write(stream);
            stream.writeUTF(getMessage());
        }

        @Override
        public void read(DataInput stream, byte revision) throws IOException{
            super.read(stream, revision);
            setMessage(stream.readUTF());
        }

        public String getMessage() {
            return message;
        }

        public String getPreviewMessage() {
            return errorMsg == null ? message : errorMsg;
        }

        public void setMessage(String message) {
            this.message = message;
            running = true;
            sleepCycles = 0;
            CharStream charStream = new CharStream(message);
            errorMsg = null;
            try {
                Codeblock programAST = Parser.parse(new TokenStream(charStream));
                interpreter = new Interpreter(programAST);
                new BlockGlobals(interpreter, this).modifyGlobals(interpreter.scopes.peek());
            } catch (Parser.SyntaxError e) {
                e.printStackTrace();
                handleError(e, charStream.lines(), charStream.chars());
            }
        }
        private void handleError(Error e, int line, int chr){
            running = false;
            StringBuilder errorMsgBuilder = new StringBuilder("[red]");
            errorMsgBuilder.append(e.getClass().getSimpleName());
            errorMsgBuilder.append(": [scarlet]");
            errorMsgBuilder.append(e.getMessage());
            errorMsgBuilder.append(" [lightgray](line ");
            errorMsgBuilder.append(line + 1);
            if(chr > -1){
                errorMsgBuilder.append(" char ");
                errorMsgBuilder.append(chr + 1);
            }
            errorMsgBuilder.append(")");
            errorMsg = errorMsgBuilder.toString();
        }
    }
}

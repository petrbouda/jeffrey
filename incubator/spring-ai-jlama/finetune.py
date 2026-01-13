from unsloth import FastLanguageModel
from datasets import load_dataset
from trl import SFTTrainer
from transformers import TrainingArguments

# Load base model
model, tokenizer = FastLanguageModel.from_pretrained(
    model_name="unsloth/Llama-3.2-1B-Instruct",
    max_seq_length=2048,
    dtype=None,  # Auto-detect
    load_in_4bit=True,
)

# Add LoRA adapters
model = FastLanguageModel.get_peft_model(
    model,
    r=16,
    target_modules=["q_proj", "k_proj", "v_proj", "o_proj",
                    "gate_proj", "up_proj", "down_proj"],
    lora_alpha=16,
    lora_dropout=0,
    bias="none",
    use_gradient_checkpointing="unsloth",
)

# Load your OQL dataset
dataset = load_dataset("json", data_files="netbeans-oql-training-dataset.jsonl")

# Format prompt
def format_prompt(example):
    return f"""### Instruction:
{example['instruction']}

### Response:
{example['output']}"""

# Training
trainer = SFTTrainer(
    model=model,
    tokenizer=tokenizer,
    train_dataset=dataset["train"],
    formatting_func=format_prompt,
    max_seq_length=2048,
    args=TrainingArguments(
        per_device_train_batch_size=2,
        gradient_accumulation_steps=4,
        warmup_steps=5,
        max_steps=100,  # Increase for better results
        learning_rate=2e-4,
        fp16=True,
        logging_steps=10,
        output_dir="outputs",
    ),
)

trainer.train()

# Save and merge LoRA weights
model.save_pretrained_merged("oql-model", tokenizer, save_method="merged_16bit")

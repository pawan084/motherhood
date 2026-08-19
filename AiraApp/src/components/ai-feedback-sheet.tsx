import { Modal, Pressable, Text, View } from 'react-native';

const REASONS = [
  'It did not answer my question',
  'It felt unsafe or worrying',
  'It repeated itself',
  'Something else',
];

export function AiFeedbackSheet({
  visible,
  onClose,
}: {
  visible: boolean;
  onClose: () => void;
}) {
  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <View className="flex-1 justify-end">
        <Pressable
          onPress={onClose}
          accessibilityRole="button"
          accessibilityLabel="Dismiss feedback"
          className="absolute inset-0">
          <View className="flex-1 bg-foreground opacity-35" />
        </Pressable>

        <View className="rounded-t-[24px] bg-background px-5 pb-6 pt-5">
          <View className="flex-row items-start justify-between gap-4">
            <View className="flex-1">
              <Text className="font-rubik-semibold text-xs uppercase tracking-widest text-brand">
                Optional feedback
              </Text>
              <Text className="mt-1 font-serif text-[26px] leading-8 text-foreground">
                What didn&apos;t help?
              </Text>
            </View>
            <Pressable
              onPress={onClose}
              accessibilityRole="button"
              accessibilityLabel="Close feedback"
              className="active:opacity-70">
              <View className="h-12 w-12 items-center justify-center rounded-full border border-border bg-card">
                <Text className="font-rubik text-3xl leading-8 text-muted-foreground">×</Text>
              </View>
            </Pressable>
          </View>

          <View className="mt-4 gap-2.5">
            {REASONS.map((reason) => (
              <Pressable key={reason} accessibilityRole="button" className="active:opacity-80">
                <View className="min-h-[48px] justify-center rounded-[16px] border border-border bg-card px-4">
                  <Text className="font-rubik-semibold text-sm text-foreground">{reason}</Text>
                </View>
              </Pressable>
            ))}
          </View>

          <Pressable onPress={onClose} accessibilityRole="button" className="mt-4 active:opacity-90">
            <View
              className="items-center rounded-[16px] bg-brand px-5 py-4"
              style={{
                shadowColor: '#7527F5',
                shadowOpacity: 0.28,
                shadowRadius: 18,
                shadowOffset: { width: 0, height: 10 },
                elevation: 8,
              }}>
              <Text className="font-rubik-semibold text-base text-brand-foreground">
                Send feedback
              </Text>
            </View>
          </Pressable>

          <Pressable
            onPress={onClose}
            accessibilityRole="button"
            className="mt-3 items-center py-1 active:opacity-60">
            <Text className="font-rubik-semibold text-sm text-brand">Skip</Text>
          </Pressable>
        </View>
      </View>
    </Modal>
  );
}

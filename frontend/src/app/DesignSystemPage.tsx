import { useState } from 'react';
import { Badge, BottomSheet, Button, Card, Input, Modal, Spinner } from '@/shared/ui';
import { useToastStore } from '@/shared/stores/toastStore';

/** 개발 모드에서만 등록하는 공용 컴포넌트 확인 화면. */
export function DesignSystemPage() {
  const [modalOpen, setModalOpen] = useState(false);
  const [sheetOpen, setSheetOpen] = useState(false);
  const notify = useToastStore((state) => state.notify);
  return (
    <section className="mx-auto min-h-dvh max-w-app space-y-6 p-page">
      <header>
        <Badge variant="dev" />
        <h1 className="mt-3 text-2xl font-bold">가치가 디자인 시스템</h1>
        <p className="mt-2 text-sm text-slate-600">
          모바일 화면과 키보드 접근성을 함께 확인합니다.
        </p>
      </header>
      <Card className="space-y-4">
        <h2 className="text-lg font-bold">버튼과 상태</h2>
        <Button fullWidth onClick={() => notify('요청이 저장되었습니다.', 'success')}>
          알림 보기
        </Button>
        <Button fullWidth variant="secondary" onClick={() => setModalOpen(true)}>
          모달 열기
        </Button>
        <Button fullWidth variant="ghost" onClick={() => setSheetOpen(true)}>
          하단 시트 열기
        </Button>
        <Button
          fullWidth
          variant="danger"
          onClick={() => notify('연결을 확인한 뒤 다시 시도해 주세요.', 'error')}
        >
          오류 알림 보기
        </Button>
        <Button fullWidth loading>
          저장 중
        </Button>
        <Button fullWidth disabled>
          선택 후 진행
        </Button>
      </Card>
      <Card className="space-y-4">
        <h2 className="text-lg font-bold">입력과 안내</h2>
        <Input
          label="학교 이메일"
          type="email"
          required
          hint="학교 웹메일 주소를 입력해 주세요."
          placeholder="student@jnu.ac.kr"
        />
        <Input
          label="인증 코드"
          inputMode="numeric"
          maxLength={6}
          error="6자리 인증 코드를 확인해 주세요."
        />
        <div className="flex flex-wrap items-center gap-2">
          <Badge variant="estimated" />
          <Badge variant="dev" />
          <Badge variant="success">완료</Badge>
          <Spinner />
        </div>
      </Card>
      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title="동승 안내"
        description="금액은 예상 요금이며 실제 택시 요금에 따라 달라질 수 있어요."
      >
        <Button fullWidth onClick={() => setModalOpen(false)}>
          확인
        </Button>
      </Modal>
      <BottomSheet
        open={sheetOpen}
        onClose={() => setSheetOpen(false)}
        title="출발 준비"
        description="동승자와 만날 장소를 확인해 주세요."
      >
        <Input label="만날 장소" placeholder="후문 앞 편의점" />
        <Button className="mt-4" fullWidth onClick={() => setSheetOpen(false)}>
          확인
        </Button>
      </BottomSheet>
    </section>
  );
}
